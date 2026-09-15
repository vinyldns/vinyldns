/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { AlertProvider } from "../../../contexts/AlertContext";
import {
  BreadcrumbProvider,
  useBreadcrumbs,
  type Crumb,
} from "../../../contexts/BreadcrumbContext";
import { Layout } from "../../../components/common/Layout";
import { frodoUser } from "../../fixtures/testData";
import type { UserProfile } from "../../../types/profile";

const profileHolder = vi.hoisted(() => ({
  profile: null as UserProfile | null,
}));

vi.mock("../../../contexts/ProfileContext", () => ({
  useProfile: () => ({
    profile: profileHolder.profile,
    loading: false,
    error: null,
    refresh: vi.fn(),
  }),
}));

function renderLayout(route = "/zones", crumbs?: Crumb[]) {
  function CrumbSetter() {
    const { setCrumbs } = useBreadcrumbs();
    React.useEffect(() => {
      if (crumbs) setCrumbs(crumbs);
    }, [setCrumbs]);
    return null;
  }
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AlertProvider>
        <BreadcrumbProvider>
          <CrumbSetter />
          <Layout>
            <div>page body</div>
          </Layout>
        </BreadcrumbProvider>
      </AlertProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  profileHolder.profile = frodoUser;
  document.documentElement.removeAttribute("data-vds-theme");
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("<Layout />", () => {
  it("renders the primary navigation links", () => {
    renderLayout();
    expect(
      screen.getByRole("link", { name: "DNS Changes" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "RecordSet Search" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Groups" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Zones" })).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Control Panel" }),
    ).not.toBeInTheDocument();
  });

  it("renders the page content passed as children", () => {
    renderLayout();
    expect(screen.getByText("page body")).toBeInTheDocument();
  });

  it("derives a breadcrumb label from the current route", () => {
    renderLayout("/zones");
    const breadcrumb = screen.getByLabelText("breadcrumb");
    expect(breadcrumb).toHaveTextContent("Zones");
  });

  it("uses breadcrumb context crumbs when provided", () => {
    renderLayout("/zones", [
      { label: "Zones", to: "/zones" },
      { label: "example.com." },
    ]);
    const breadcrumb = screen.getByLabelText("breadcrumb");
    expect(breadcrumb).toHaveTextContent("example.com.");
  });

  it("toggles the sidebar collapsed state", async () => {
    renderLayout();
    const toggle = screen.getByRole("button", { name: "Collapse sidebar" });
    await userEvent.click(toggle);
    expect(
      screen.getByRole("button", { name: "Expand sidebar" }),
    ).toBeInTheDocument();
  });

  it("switches the document theme attribute when the theme toggle is used", async () => {
    renderLayout();
    expect(document.documentElement.getAttribute("data-vds-theme")).toBe(
      "light",
    );
    await userEvent.click(screen.getByRole("button", { name: "Toggle theme" }));
    expect(document.documentElement.getAttribute("data-vds-theme")).toBe(
      "dark",
    );
  });

  it("renders old portal switch off by default", () => {
    renderLayout();
    const btn = screen.getByRole("button", { name: /Switch to old portal/i });
    expect(btn).toBeInTheDocument();
    expect(
      btn.querySelector(".vds-theme-toggle__track"),
    ).not.toHaveClass("vds-theme-toggle__track--dark");
  });

  it("navigates to old portal when switch is toggled on", async () => {
    const originalLocation = window.location;
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { ...originalLocation, href: "" },
    });

    renderLayout();
    await userEvent.click(
      screen.getByRole("button", { name: /Switch to old portal/i }),
    );

    await waitFor(() => expect(window.location.href).toBe("/"));
    expect(document.cookie).toContain("ui_mode=old");

    Object.defineProperty(window, "location", {
      configurable: true,
      value: originalLocation,
    });
  });

  it("renders the user menu button for an authenticated profile", () => {
    renderLayout();
    expect(
      screen.getByRole("button", { name: new RegExp(frodoUser.userName) }),
    ).toBeInTheDocument();
  });

  it("hides the user menu when there is no profile", () => {
    profileHolder.profile = null;
    renderLayout();
    expect(
      screen.queryByRole("button", { name: new RegExp(frodoUser.userName) }),
    ).not.toBeInTheDocument();
  });

  it("opens the regenerate-credentials modal from the user menu", async () => {
    renderLayout();
    await userEvent.click(
      screen.getByRole("button", { name: new RegExp(frodoUser.userName) }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: /Regenerate Credentials/ }),
    );
    expect(screen.getByText("Regenerate Credentials?")).toBeInTheDocument();
  });

  it("calls the regenerate endpoint and shows a success alert on confirm", async () => {
    const fetchMock = vi.fn().mockResolvedValue({});
    vi.stubGlobal("fetch", fetchMock);

    renderLayout();
    await userEvent.click(
      screen.getByRole("button", { name: new RegExp(frodoUser.userName) }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: /Regenerate Credentials/ }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Yes, Regenerate" }),
    );

    expect(fetchMock).toHaveBeenCalledWith("/regenerate-creds", {
      method: "POST",
    });
    await waitFor(() =>
      expect(
        screen.getByText("Credentials regenerated successfully"),
      ).toBeInTheDocument(),
    );
    vi.unstubAllGlobals();
  });

  it("logs out by navigating to the logout endpoint", async () => {
    const originalLocation = window.location;
    const assignMock = vi.fn();
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { ...originalLocation, assign: assignMock },
    });

    renderLayout();
    await userEvent.click(
      screen.getByRole("button", { name: new RegExp(frodoUser.userName) }),
    );
    await userEvent.click(screen.getByRole("button", { name: /Logout/ }));

    await waitFor(() => expect(assignMock).toHaveBeenCalledWith("/logout"));

    Object.defineProperty(window, "location", {
      configurable: true,
      value: originalLocation,
    });
  });
});
