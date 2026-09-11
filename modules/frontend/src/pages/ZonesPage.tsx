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

import React, { useState, useCallback, useEffect, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ZonesTable } from "../components/zones/ZonesTable";
import { AbandonedZonesTable } from "../components/zones/AbandonedZonesTable";
import { ZoneForm } from "../components/zones/ZoneForm";
import { Pagination, PaginatedSection } from "../components/common/Pagination";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { TimeFilterDropdown } from "../components/common/TimeFilterDropdown";
import type { TimeRange } from "../components/common/TimeFilterDropdown";
import { useZones, useDeletedZones } from "../hooks/useZones";
import { groupsService } from "../services/groupsService";
import { zonesService } from "../services/zonesService";
import { useProfile } from "../contexts/ProfileContext";
import type { Zone, ZoneCount } from "../types/zone";

// ── Tab type ───────────────────────────────────────────────────────────────────
type MainTab = "myZones" | "allZones" | "abandonedZones";
type AbandonedSubTab = "myAbandoned" | "allAbandoned";

export function ZonesPage() {
  const queryClient = useQueryClient();
  const { profile } = useProfile();
  const isSuper = profile?.isSuper ?? false;
  const isSupport = profile?.isSupport ?? false;
  const [searchParams, setSearchParams] = useSearchParams();

  // Restore tab + paging cursor from URL on mount
  const initMainTab = ["myZones", "allZones", "abandonedZones"].includes(
    searchParams.get("tab") ?? "",
  )
    ? (searchParams.get("tab") as MainTab)
    : "myZones";
  const initPaging = (() => {
    const next = searchParams.get("next") ?? undefined;
    const pn = parseInt(searchParams.get("pn") ?? "0", 10);
    const sk = (searchParams.get("sk") ?? "").split(",").filter(Boolean);
    return next || pn > 0 ? { next, pageNum: pn, startKeys: sk } : undefined;
  })();

  // ── Tab state ────────────────────────────────────────────────────────────────
  const [mainTab, setMainTab] = useState<MainTab>(initMainTab);
  const [abandonedSubTab, setAbandonedSubTab] =
    useState<AbandonedSubTab>("myAbandoned");
  const [tabFading, setTabFading] = useState(false);
  const [showFilters, setShowFilters] = useState(true);

  // ── Modals / forms ───────────────────────────────────────────────────────────
  const [showConnectForm, setShowConnectForm] = useState(false);
  const [showCards, setShowCards] = useState(true);

  // ── Per-tab search inputs (committed on Search / Enter) ──────────────────────
  const [myZonesInput, setMyZonesInput] = useState("");
  const [allZonesInput, setAllZonesInput] = useState("");
  const [abandonedInput, setAbandonedInput] = useState("");

  // Committed queries
  const [myZonesQuery, setMyZonesQuery] = useState("");
  const [allZonesQuery, setAllZonesQuery] = useState("");
  const [abandonedQuery, setAbandonedQuery] = useState("");

  // ── Per-tab filters ───────────────────────────────────────────────────────────
  const [myZonesByGroup, setMyZonesByGroup] = useState(false);
  const [allZonesByGroup, setAllZonesByGroup] = useState(false);
  const [myZonesHidePtr, setMyZonesHidePtr] = useState(false);
  const [allZonesHidePtr, setAllZonesHidePtr] = useState(false);

  // ── Fancy filter dropdowns ─────────────────────────────────────────────────────
  const [accessFilter, setAccessFilter] = useState<"shared" | "private" | null>(
    null,
  );
  const [accessDropdownOpen, setAccessDropdownOpen] = useState(false);
  const accessDropdownRef = useRef<HTMLDivElement>(null);

  // ── Abandoned zone filters ────────────────────────────────────────────────────
  const [abanAccessFilter, setAbanAccessFilter] = useState<
    "shared" | "private" | null
  >(null);
  const [abanByGroup, setAbanByGroup] = useState(false);
  const [abanAccessDropdownOpen, setAbanAccessDropdownOpen] = useState(false);
  const abanAccessDropdownRef = useRef<HTMLDivElement>(null);

  // ── Search suggestions ────────────────────────────────────────────────────────
  const [mySuggestionsOpen, setMySuggestionsOpen] = useState(false);
  const [allSuggestionsOpen, setAllSuggestionsOpen] = useState(false);
  const mySuggestionsRef = useRef<HTMLDivElement>(null);
  const allSuggestionsRef = useRef<HTMLDivElement>(null);

  // Debounced values for suggestions queries
  const [myZonesSuggestQuery, setMyZonesSuggestQuery] = useState("");
  const [allZonesSuggestQuery, setAllZonesSuggestQuery] = useState("");

  // ── Client-side email filter ───────────────────────────────────────────────────
  const [emailFilter, setEmailFilter] = useState("");

  // ── Time filters ──────────────────────────────────────────────────────────────
  const [zoneTimeRange, setZoneTimeRange] = useState<TimeRange>("all");
  const [zoneDateFrom, setZoneDateFrom] = useState("");
  const [zoneDateTo, setZoneDateTo] = useState("");
  const [abanTimeRange, setAbanTimeRange] = useState<TimeRange>("all");
  const [abanDateFrom, setAbanDateFrom] = useState("");
  const [abanDateTo, setAbanDateTo] = useState("");

  // ── Zones hooks ───────────────────────────────────────────────────────────────
  const myZones = useZones(
    false,
    !myZonesHidePtr,
    initMainTab === "myZones" ? initPaging : undefined,
  );
  const allZones = useZones(
    true,
    !allZonesHidePtr,
    initMainTab === "allZones" ? initPaging : undefined,
  );

  const myAbandoned = useDeletedZones(false, true); // always enabled so deletedZones.length is available as fallback
  const allAbandoned = useDeletedZones(true, mainTab === "abandonedZones");
  const activeAbandonedHook =
    abandonedSubTab === "myAbandoned" ? myAbandoned : allAbandoned;

  // Sync mainTab + active tab paging cursor to URL so back-navigation restores state
  const activePaging =
    mainTab === "allZones" ? allZones.paging : myZones.paging;
  const zonesStartKeysStr = activePaging.startKeys
    .filter(Boolean)
    .map(String)
    .join(",");
  useEffect(() => {
    const params: Record<string, string> = {};
    if (mainTab !== "myZones") params.tab = mainTab;
    if (mainTab !== "abandonedZones") {
      if (activePaging.next != null) params.next = String(activePaging.next);
      if (activePaging.pageNum > 0) params.pn = String(activePaging.pageNum);
      if (zonesStartKeysStr) params.sk = zonesStartKeysStr;
    }
    setSearchParams(params, { replace: true });
  }, [mainTab, activePaging.next, activePaging.pageNum, zonesStartKeysStr]);

  // ── Backend IDs & groups (for ZoneForm) ──────────────────────────────────────
  // Super users see all groups (ignoreAccess=true); support and regular users
  // see only groups they belong to (ignoreAccess=false). maxItems=3000 matches old behaviour.
  const { data: groupsData } = useQuery({
    queryKey: ["all-groups", isSuper],
    queryFn: async () => {
      const res = await groupsService.getGroups(isSuper, "", 3000);
      return res.data.groups ?? [];
    },
  });

  const { data: backendIds } = useQuery({
    queryKey: ["backend-ids"],
    queryFn: async () => {
      const res = await zonesService.getBackendIds();
      return res.data ?? [];
    },
  });

  const { data: zonesCount } = useQuery({
    queryKey: ["zones-count"],
    queryFn: async () => {
      const res = await zonesService.countZones();
      return res.data as ZoneCount;
    },
    staleTime: 5 * 60 * 1000, // treat as fresh for 5 min — avoids re-fetching on every tab switch/focus
  });

  // ── Insight data ─────────────────────────────────────────────────────────────
  const { data: insightMyZones } = useQuery({
    queryKey: ["insight-my-zones"],
    queryFn: async () => {
      const res = await zonesService.getZones(
        100,
        undefined,
        undefined,
        false,
        false,
        true,
      );
      return res.data.zones ?? [];
    },
  });

  const { data: insightAllZones } = useQuery({
    queryKey: ["insight-all-zones"],
    queryFn: async () => {
      const res = await zonesService.getZones(
        100,
        undefined,
        undefined,
        false,
        true,
        true,
      );
      return res.data.zones ?? [];
    },
  });

  const { data: insightAbandonedData } = useQuery({
    queryKey: ["insight-abandoned-zones"],
    queryFn: async () => {
      const res = await zonesService.getDeletedZones(
        100,
        undefined,
        undefined,
        false,
      );
      return res.data.zonesDeletedInfo ?? [];
    },
  });
  const insightAbandonedCount = zonesCount?.abandonedCount ?? null;
  const insightMyCount = zonesCount?.myZonesCount ?? null;
  const insightAllCount = zonesCount?.totalCount ?? null;

  // ── Search suggestions queries (ignoreAccess + includeReverse like old portal) ─
  const { data: mySuggestData } = useQuery({
    queryKey: ["zone-suggestions-my", myZonesSuggestQuery],
    queryFn: async () => {
      const res = await zonesService.getZones(
        10,
        undefined,
        myZonesSuggestQuery,
        false,
        false,
        true,
      );
      return res.data.zones ?? [];
    },
    enabled: myZonesSuggestQuery.length > 0 && !myZonesByGroup,
  });

  const { data: allSuggestData } = useQuery({
    queryKey: ["zone-suggestions-all", allZonesSuggestQuery],
    queryFn: async () => {
      const res = await zonesService.getZones(
        10,
        undefined,
        allZonesSuggestQuery,
        false,
        true,
        true,
      );
      return res.data.zones ?? [];
    },
    enabled: allZonesSuggestQuery.length > 0 && !allZonesByGroup,
  });

  const mySuggestions = mySuggestData ?? [];
  const allSuggestions = allSuggestData ?? [];

  const fmtAge = (days: number): string => {
    if (days < 1) return "Today";
    if (days < 30) return `${days}d`;
    if (days < 365) return `${Math.floor(days / 30)}mo`;
    const y = Math.floor(days / 365);
    const m = Math.floor((days % 365) / 30);
    return m > 0 ? `${y}y ${m}mo` : `${y}y`;
  };

  // displaySource: used for card counts and filter option detection only.
  // When Hide PTR is on, use zonesForTab (API already excluded PTR via includeReverse=false).
  // insightSource always fetches includeReverse=true.
  const insightSource =
    mainTab === "allZones" ? (insightAllZones ?? []) : (insightMyZones ?? []);
  const zonesForTab = mainTab === "allZones" ? allZones.zones : myZones.zones;
  const filterSource = insightSource.length > 0 ? insightSource : zonesForTab;
  const activeHidePtr =
    mainTab === "allZones" ? allZonesHidePtr : myZonesHidePtr;
  const isPtr = (z: Zone) =>
    z.name.includes("in-addr.arpa") || z.name.includes("ip6.arpa");
  const displaySource = activeHidePtr ? zonesForTab : filterSource;
  const byGroupActive =
    mainTab === "allZones" ? allZonesByGroup : myZonesByGroup;

  const isWithinRange = (
    dateStr: string | undefined,
    range: TimeRange,
    from: string,
    to: string,
  ): boolean => {
    if (range === "all") return true;
    if (!dateStr) return true;
    const ts = new Date(dateStr).getTime();
    const now = Date.now();
    if (range === "1d") return ts >= now - 86400000;
    if (range === "7d") return ts >= now - 7 * 86400000;
    if (range === "30d") return ts >= now - 30 * 86400000;
    if (range === "90d") return ts >= now - 90 * 86400000;
    if (range === "custom") {
      // Append T00:00:00 / T23:59:59 without timezone so JS parses as local (browser) time,
      // not UTC — ensuring the filter respects the user's local timezone.
      if (from && ts < new Date(from + "T00:00:00").getTime()) return false;
      if (to && ts > new Date(to + "T23:59:59").getTime()) return false;
    }
    return true;
  };

  const byGroupSearchActive = byGroupActive && !!emailFilter;
  const anyFilterActive = !!(
    (!byGroupSearchActive && emailFilter) ||
    accessFilter ||
    zoneTimeRange !== "all"
  );
  // accessServerTotal: server-known count for the current access filter.
  // When Hide PTR is ON, subtract PTR zones from the total (since they are excluded from the table).
  const accessServerTotal =
    accessFilter === "shared"
      ? activeHidePtr
        ? zonesCount != null
          ? zonesCount.sharedCount - (zonesCount.sharedPtrCount ?? 0)
          : null
        : (zonesCount?.sharedCount ?? null)
      : accessFilter === "private"
        ? activeHidePtr
          ? zonesCount != null
            ? zonesCount.privateCount - (zonesCount.privatePtrCount ?? 0)
            : null
          : (zonesCount?.privateCount ?? null)
        : null;

  // accessFilter alone (no email/time) keeps server pagination active.
  const accessFilterOnly =
    !!accessFilter && !emailFilter && zoneTimeRange === "all";

  // Access filter (with no email/time) keeps server pagination active.
  const anyServerCompatibleFilter =
    !!accessFilter &&
    !emailFilter &&
    zoneTimeRange === "all" &&
    !byGroupSearchActive;

  const activeNameQuery = mainTab === "allZones" ? allZonesQuery : myZonesQuery;
  const activeSuggestions =
    mainTab === "allZones" ? allSuggestions : mySuggestions;
  const clientFilterBase = byGroupActive
    ? zonesForTab
    : activeNameQuery
      ? activeSuggestions.length > 0
        ? activeSuggestions
        : zonesForTab
      : activeHidePtr
        ? zonesForTab
        : filterSource;

  const secondaryFilterActive = !!(accessFilter || zoneTimeRange !== "all");
  const displayedZones =
    anyFilterActive || (byGroupSearchActive && secondaryFilterActive)
      ? clientFilterBase.filter((z) => {
          const matchesSearch = byGroupActive
            ? true // server-side admin group filter already applied
            : !emailFilter ||
              z.name.toLowerCase().includes(emailFilter.toLowerCase());
          const matchesPtr = !activeHidePtr || !isPtr(z);
          const matchesAccess =
            !accessFilter || (accessFilter === "shared" ? z.shared : !z.shared);
          const matchesTime = isWithinRange(
            z.latestSync,
            zoneTimeRange,
            zoneDateFrom,
            zoneDateTo,
          );
          return matchesSearch && matchesPtr && matchesAccess && matchesTime;
        })
      : null;

  const renderedZones = anyServerCompatibleFilter
    ? zonesForTab
    : (displayedZones ?? zonesForTab);

  const searchBase = byGroupActive
    ? zonesForTab
    : activeNameQuery
      ? activeSuggestions.length > 0
        ? activeSuggestions
        : zonesForTab
      : displaySource; // no search — fall back to full display set

  // ── Abandoned zones client-side filtering ──────────────────────────────────────────
  const abandonedZonesRaw = activeAbandonedHook.deletedZones;
  // Always show both access options for abandoned zones (no global API count available for this subset)
  const abanHasShared = true;
  const abanHasPrivate = true;
  const anyAbandonedFilterActive = !!(
    abandonedInput ||
    abanAccessFilter ||
    abanByGroup ||
    abanTimeRange !== "all"
  );
  const displayedAbandonedZones = anyAbandonedFilterActive
    ? abandonedZonesRaw.filter((item) => {
        const z = item.zoneChange.zone;
        const matchesSearch =
          !abandonedInput ||
          (abanByGroup
            ? (item.adminGroupName ?? z.adminGroupId ?? "")
                .toLowerCase()
                .includes(abandonedInput.toLowerCase())
            : z.name.toLowerCase().includes(abandonedInput.toLowerCase()) ||
              z.email.toLowerCase().includes(abandonedInput.toLowerCase()));
        const matchesTime = isWithinRange(
          z.updated,
          abanTimeRange,
          abanDateFrom,
          abanDateTo,
        );
        return matchesSearch && matchesTime;
      })
    : abandonedZonesRaw;

  const cardSource: Zone[] =
    mainTab === "abandonedZones"
      ? displayedAbandonedZones.map((i) => i.zoneChange.zone)
      : anyServerCompatibleFilter
        ? zonesForTab
        : (displayedZones ?? searchBase);

  const cardLoading =
    mainTab === "abandonedZones"
      ? insightAbandonedData === undefined || zonesCount === undefined
      : mainTab === "allZones"
        ? insightAllZones === undefined || zonesCount === undefined
        : insightMyZones === undefined || zonesCount === undefined;

  const anyFilterNow =
    mainTab === "abandonedZones" ? anyAbandonedFilterActive : anyFilterActive;
  const cardActiveCount = cardSource.filter(
    (z) => z.status === "Active",
  ).length;
  const cardSyncingCount = cardSource.filter(
    (z) => z.status === "Syncing",
  ).length;
  const cardIncidentCount = cardSource.filter(
    (z) => z.status !== "Active" && z.status !== "Deleted",
  ).length;
  const cardSharedCount = cardSource.filter((z) => !!z.shared).length;
  const cardPtrCount = activeHidePtr
    ? 0
    : cardSource.filter((z) => isPtr(z)).length;
  const cardNeverSynced = cardSource.filter((z) => !z.latestSync).length;
  const cardRecentlySynced = cardSource.filter(
    (z) =>
      Boolean(z.latestSync) &&
      Date.now() - new Date(z.latestSync!).getTime() <= 7 * 86400000,
  ).length;
  const cardNewThisMonth = cardSource.filter(
    (z) =>
      Boolean(z.created) &&
      Date.now() - new Date(z.created!).getTime() <= 30 * 86400000,
  ).length;
  const cardOldestAgeDays: number | null = (() => {
    const times = cardSource
      .filter((z) => Boolean(z.created))
      .map((z) => new Date(z.created!).getTime());
    return times.length
      ? Math.floor((Date.now() - Math.min(...times)) / 86400000)
      : null;
  })();

  const cardContextLabel =
    mainTab === "abandonedZones"
      ? "Abandoned"
      : mainTab === "allZones"
        ? "All Zones"
        : "My Zones";
  const cardFiltered =
    mainTab === "abandonedZones" ? anyAbandonedFilterActive : anyFilterActive;
  // Current page 1-based range for pagination labels and lifecycle card footnote
  const activePageNum =
    mainTab === "abandonedZones"
      ? activeAbandonedHook.pageNum
      : mainTab === "allZones"
        ? allZones.pageNum
        : myZones.pageNum;
  const pageSize = 100;

  // cardTotal: authoritative count for the current view — must be declared before display vars
  // When Hide PTR is ON, subtract PTR count from total for accurate non-PTR count.
  // For myZones, super/support users see all zones (myZonesCount === totalCount), so the same
  // subtraction applies. Regular users don't have a per-user PTR breakdown, so leave as-is.

  // Server-authoritative total for the abandoned tab when access filter is active.
  // abandonedSharedCount/abandonedPrivateCount are derived from zonesCount below — inline here to avoid forward refs.
  const _abandonedSharedCount = zonesCount?.abandonedSharedCount ?? null;
  const _abandonedTotal = zonesCount?.abandonedCount ?? null;
  const _abandonedPrivateCount =
    _abandonedTotal != null && _abandonedSharedCount != null
      ? _abandonedTotal - _abandonedSharedCount
      : null;
  const abandonedAccessServerTotal =
    abanAccessFilter === "shared"
      ? (_abandonedSharedCount ?? null)
      : abanAccessFilter === "private"
        ? (_abandonedPrivateCount ?? null)
        : null;
  // Only name/time filter = client-side; access-only filter for abandoned = server count is known.
  const abanAccessFilterOnly =
    !!abanAccessFilter && !abandonedInput && abanTimeRange === "all";
  const _isAbandonedTab = mainTab === "abandonedZones";
  // Only use global abandoned counts when showing ALL abandoned zones (not user-specific My Zones)
  const _isAllAbandonedSubTab =
    _isAbandonedTab && abandonedSubTab === "allAbandoned";
  const _isMyAbandonedSubTab =
    _isAbandonedTab && abandonedSubTab === "myAbandoned";

  // Server-authoritative totals for My Abandoned sub-tab with access filter.
  // For super/support: fall back to platform-wide counts (they see all zones either way).
  // For regular users: null if the backend doesn't return the per-user breakdown.
  const _myAbandonedTotal =
    zonesCount?.myAbandonedCount ??
    (isSuper || isSupport ? _abandonedTotal : null);
  const _myAbandonedSharedTotal =
    zonesCount?.myAbandonedSharedCount ??
    (isSuper || isSupport ? _abandonedSharedCount : null);
  const _myAbandonedPrivateTotal =
    _myAbandonedTotal != null && _myAbandonedSharedTotal != null
      ? _myAbandonedTotal - _myAbandonedSharedTotal
      : null;
  const myAbandonedAccessServerTotal =
    abanAccessFilter === "shared"
      ? _myAbandonedSharedTotal
      : abanAccessFilter === "private"
        ? _myAbandonedPrivateTotal
        : null;

  const serverTotalForTab =
    mainTab === "abandonedZones"
      ? _isAllAbandonedSubTab
        ? abanAccessFilterOnly && abandonedAccessServerTotal !== null
          ? abandonedAccessServerTotal
          : _abandonedTotal
        : (zonesCount?.myAbandonedCount ??
          (isSuper || isSupport ? _abandonedTotal : null))
      : mainTab === "allZones"
        ? activeHidePtr && zonesCount != null
          ? zonesCount.totalCount - zonesCount.ptrCount
          : insightAllCount
        : activeHidePtr && zonesCount != null && (isSuper || isSupport)
          ? zonesCount.totalCount - zonesCount.ptrCount
          : insightMyCount;
  const cardTotal =
    !_isAbandonedTab && accessFilterOnly && accessServerTotal !== null
      ? accessServerTotal
      : _isAllAbandonedSubTab &&
          abanAccessFilterOnly &&
          abandonedAccessServerTotal !== null
        ? abandonedAccessServerTotal
        : _isMyAbandonedSubTab &&
            abanAccessFilterOnly &&
            myAbandonedAccessServerTotal !== null
          ? myAbandonedAccessServerTotal
          : !anyFilterNow && serverTotalForTab !== null
            ? serverTotalForTab
            : _isAllAbandonedSubTab &&
                serverTotalForTab !== null &&
                !abandonedInput &&
                abanTimeRange === "all"
              ? serverTotalForTab
              : cardSource.length;

  const pageStart = cardTotal > 0 ? activePageNum * pageSize + 1 : 0;

  const pageEnd =
    cardTotal === 0
      ? 0
      : !_isAbandonedTab && accessFilterOnly && accessServerTotal !== null
        ? Math.min((activePageNum + 1) * pageSize, accessServerTotal)
        : _isAllAbandonedSubTab &&
            abanAccessFilterOnly &&
            abandonedAccessServerTotal !== null
          ? Math.min((activePageNum + 1) * pageSize, abandonedAccessServerTotal)
          : _isMyAbandonedSubTab &&
              abanAccessFilterOnly &&
              myAbandonedAccessServerTotal !== null
            ? Math.min(
                (activePageNum + 1) * pageSize,
                myAbandonedAccessServerTotal,
              )
            : pageStart - 1 + cardSource.length;

  const pageRangeLabel = `${pageStart.toLocaleString()}–${pageEnd.toLocaleString()} of ${cardTotal.toLocaleString()}`;

  // Insight card counts use zonesCount platform totals (never page-level).
  // For abandonedZones tab: use dedicated abandoned counts from the server.
  // For allZones/myZones: use activeCount/syncingCount/shared/private from zonesCount.
  // When Hide PTR is ON, subtract PTR zones from shared/private/total.
  const isAbandonedTab = mainTab === "abandonedZones";

  // Abandoned-tab totals derived server-side.
  // "All" subtab: use platform-wide abandoned counts.
  // "My" subtab: use per-user abandoned counts (my* fields), fall back to page-level.
  const isAllAbandonedSubTab =
    isAbandonedTab && abandonedSubTab === "allAbandoned";
  const isMyAbandonedSubTab =
    isAbandonedTab && abandonedSubTab === "myAbandoned";
  const myAbandonedRegularUser = isMyAbandonedSubTab && !isSuper && !isSupport;

  // All-abandoned counts (only used on allAbandoned subtab)
  const abandonedTotal = isAllAbandonedSubTab
    ? (zonesCount?.abandonedCount ?? null)
    : null;
  const abandonedPtrCount = isAllAbandonedSubTab
    ? (zonesCount?.abandonedPtrCount ?? null)
    : null;
  const abandonedSharedCount = isAllAbandonedSubTab
    ? (zonesCount?.abandonedSharedCount ?? null)
    : null;
  const abandonedPrivateCount =
    abandonedTotal != null && abandonedSharedCount != null
      ? abandonedTotal - abandonedSharedCount
      : null;
  const abandonedNonPtrCount =
    abandonedTotal != null && abandonedPtrCount != null
      ? abandonedTotal - abandonedPtrCount
      : null;

  // My-abandoned counts — server-authoritative with fallback for old backends.
  // For super/support, they see all zones, so fall back to platform-wide abandoned counts.
  // For regular users, fall back to current-page counts until backend is updated.
  const myAbandonedServer = isMyAbandonedSubTab
    ? (zonesCount?.myAbandonedCount ??
      (isSuper || isSupport ? (_abandonedTotal ?? null) : cardSource.length))
    : null;
  const myAbandonedPtrServer = isMyAbandonedSubTab
    ? (zonesCount?.myAbandonedPtrCount ??
      (isSuper || isSupport
        ? (zonesCount?.abandonedPtrCount ?? null)
        : cardSource.filter((z) => isPtr(z)).length))
    : null;
  const myAbandonedSharedServer = isMyAbandonedSubTab
    ? (zonesCount?.myAbandonedSharedCount ??
      (isSuper || isSupport
        ? (zonesCount?.abandonedSharedCount ?? null)
        : cardSource.filter((z) => !!z.shared).length))
    : null;
  const myAbandonedPrivateServer =
    myAbandonedServer != null && myAbandonedSharedServer != null
      ? myAbandonedServer - myAbandonedSharedServer
      : null;
  const myAbandonedNonPtrServer =
    myAbandonedServer != null && myAbandonedPtrServer != null
      ? myAbandonedServer - myAbandonedPtrServer
      : null;

  // Non-abandoned PTR-aware totals.
  // For My Zones tab, platform-wide sharedCount/privateCount are only accurate for super/support
  // (who see all zones). Regular users use server-authoritative per-user counts (my* fields),
  // with card-level page counts as fallback when the backend hasn't been updated yet.
  const myZonesRegularUser = mainTab === "myZones" && !isSuper && !isSupport;
  // Intermediate per-user server counts — fall back to page-level if missing (old backend).
  const mySharedServer = zonesCount?.mySharedCount ?? cardSharedCount;
  const myPrivateServer =
    zonesCount?.myPrivateCount ?? cardTotal - cardSharedCount;
  const myPtrServer = zonesCount?.myPtrCount ?? cardPtrCount;
  const myActiveServer = zonesCount?.myActiveCount ?? cardActiveCount;
  const mySyncingServer = zonesCount?.mySyncingCount ?? cardSyncingCount;

  const sharedCountForDisplay =
    zonesCount == null
      ? null
      : myZonesRegularUser
        ? activeHidePtr
          ? mySharedServer - Math.min(mySharedServer, myPtrServer)
          : mySharedServer
        : activeHidePtr
          ? zonesCount.sharedCount - (zonesCount.sharedPtrCount ?? 0)
          : zonesCount.sharedCount;
  const privateCountForDisplay =
    zonesCount == null
      ? null
      : myZonesRegularUser
        ? activeHidePtr
          ? myPrivateServer - Math.max(0, myPtrServer - mySharedServer)
          : myPrivateServer
        : activeHidePtr
          ? zonesCount.privateCount - (zonesCount.privatePtrCount ?? 0)
          : zonesCount.privateCount;
  const totalCountForDisplay =
    zonesCount == null
      ? null
      : myZonesRegularUser
        ? activeHidePtr
          ? zonesCount.myZonesCount - myPtrServer
          : zonesCount.myZonesCount
        : activeHidePtr
          ? zonesCount.totalCount - (zonesCount.ptrCount ?? 0)
          : zonesCount.totalCount;

  const displayActiveCount = isAbandonedTab
    ? null
    : myZonesRegularUser
      ? myActiveServer
      : (zonesCount?.activeCount ?? null);
  const displaySyncingCount = isAbandonedTab
    ? null
    : myZonesRegularUser
      ? mySyncingServer
      : (zonesCount?.syncingCount ?? null);
  // Resolved PTR / non-PTR / shared / private for the abandoned card — prefer server counts.
  const resolvedAbandonedPtrCount = isAllAbandonedSubTab
    ? abandonedPtrCount
    : myAbandonedPtrServer;
  const resolvedAbandonedNonPtrCount = isAllAbandonedSubTab
    ? abandonedNonPtrCount
    : myAbandonedNonPtrServer;
  const resolvedAbandonedSharedCount = isAllAbandonedSubTab
    ? abandonedSharedCount
    : myAbandonedSharedServer;
  const resolvedAbandonedPrivateCount = isAllAbandonedSubTab
    ? abandonedPrivateCount
    : myAbandonedPrivateServer;
  const resolvedAbandonedTotal = isAllAbandonedSubTab
    ? abandonedTotal
    : myAbandonedServer;
  const displaySharedCount = isAbandonedTab
    ? abanAccessFilter === "shared"
      ? (resolvedAbandonedSharedCount ?? null)
      : abanAccessFilter === "private"
        ? 0
        : resolvedAbandonedSharedCount
    : accessFilter === "shared"
      ? (sharedCountForDisplay ?? null)
      : accessFilter === "private"
        ? 0
        : (sharedCountForDisplay ?? null);
  const displayPrivateCount = isAbandonedTab
    ? abanAccessFilter === "private"
      ? (resolvedAbandonedPrivateCount ?? null)
      : abanAccessFilter === "shared"
        ? 0
        : resolvedAbandonedPrivateCount
    : accessFilter === "private"
      ? (privateCountForDisplay ?? null)
      : accessFilter === "shared"
        ? 0
        : (privateCountForDisplay ?? null);
  const accessSplitTotal = isAbandonedTab
    ? isAllAbandonedSubTab
      ? (abandonedTotal ?? cardTotal)
      : (myAbandonedServer ?? cardTotal)
    : (totalCountForDisplay ?? cardTotal);
  // healthBase: abandoned tab uses total abandoned count; others use PTR-aware total.
  const healthBase = isAbandonedTab
    ? (resolvedAbandonedTotal ?? cardTotal)
    : (totalCountForDisplay ?? cardTotal);
  const resolvedAbandonedTotal_helper = resolvedAbandonedTotal ?? 0;
  const healthPct = isAbandonedTab
    ? resolvedAbandonedTotal_helper > 0 && resolvedAbandonedPtrCount != null
      ? Math.floor(
          (resolvedAbandonedPtrCount / resolvedAbandonedTotal_helper) * 100,
        )
      : 0
    : healthBase > 0
      ? Math.min(
          100,
          Math.floor(((displayActiveCount ?? 0) / healthBase) * 100),
        )
      : 0;
  // Header shows health% for all tabs (PTR% for abandoned, active% for others).
  const displayZoneHealthHeader = zonesCount != null ? `${healthPct}%` : null;
  const skeletonBlue = (
    <span className="vds-insight-skeleton vds-insight-skeleton--blue" />
  );
  const skeletonTeal = (
    <span className="vds-insight-skeleton vds-insight-skeleton--teal" />
  );
  const skeletonPurple = (
    <span className="vds-insight-skeleton vds-insight-skeleton--purple" />
  );
  const skeletonAmber = (
    <span className="vds-insight-skeleton vds-insight-skeleton--amber" />
  );

  const card1RefLabel =
    mainTab === "abandonedZones" ? "Total abandoned" : "Platform";
  const card1ViewLabel = mainTab === "abandonedZones" ? "Showing" : "In view";
  const card1RefCount = isAbandonedTab
    ? isAllAbandonedSubTab
      ? insightAbandonedCount
      : insightAbandonedCount
    : mainTab === "myZones"
      ? insightAllCount
      : insightAllCount;

  // Access options: use server-authoritative counts when available, otherwise show both
  const hasShared = zonesCount != null ? zonesCount.sharedCount > 0 : true;
  const hasPrivate = zonesCount != null ? zonesCount.privateCount > 0 : true;
  // ── Sync committed queries → hooks ───────────────────────────────────────────
  useEffect(() => {
    myZones.search(myZonesQuery, myZonesByGroup);
  }, [myZonesQuery, myZonesByGroup]);
  useEffect(() => {
    allZones.search(allZonesQuery, allZonesByGroup);
  }, [allZonesQuery, allZonesByGroup]);
  useEffect(() => {
    myAbandoned.search(abandonedQuery);
  }, [abandonedQuery]);
  useEffect(() => {
    allAbandoned.search(abandonedQuery);
  }, [abandonedQuery]);

  // Sync access filter choice → hooks (converts UI string to API numeric param)
  const apiAccessFilter =
    accessFilter === "shared" ? 0 : accessFilter === "private" ? 1 : undefined;
  useEffect(() => {
    myZones.setAccessFilter(apiAccessFilter);
  }, [accessFilter]);
  useEffect(() => {
    allZones.setAccessFilter(apiAccessFilter);
  }, [accessFilter]);

  // Sync abandoned access filter → hooks
  const abanApiAccess =
    abanAccessFilter === "shared"
      ? 0
      : abanAccessFilter === "private"
        ? 1
        : undefined;
  useEffect(() => {
    myAbandoned.setAccessFilter(abanApiAccess);
  }, [abanAccessFilter]);
  useEffect(() => {
    allAbandoned.setAccessFilter(abanApiAccess);
  }, [abanAccessFilter]);

  // ── Debounce search inputs for suggestions ────────────────────────────────────
  useEffect(() => {
    if (myZonesByGroup) return;
    const timer = setTimeout(() => setMyZonesSuggestQuery(myZonesInput), 300);
    return () => clearTimeout(timer);
  }, [myZonesInput, myZonesByGroup]);

  useEffect(() => {
    if (allZonesByGroup) return;
    const timer = setTimeout(() => setAllZonesSuggestQuery(allZonesInput), 300);
    return () => clearTimeout(timer);
  }, [allZonesInput, allZonesByGroup]);

  // ── Outside-click handler for filter dropdowns ───────────────────────────────
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (
        accessDropdownRef.current &&
        !accessDropdownRef.current.contains(e.target as Node)
      ) {
        setAccessDropdownOpen(false);
      }
      if (
        abanAccessDropdownRef.current &&
        !abanAccessDropdownRef.current.contains(e.target as Node)
      ) {
        setAbanAccessDropdownOpen(false);
      }
      if (
        mySuggestionsRef.current &&
        !mySuggestionsRef.current.contains(e.target as Node)
      ) {
        setMySuggestionsOpen(false);
      }
      if (
        allSuggestionsRef.current &&
        !allSuggestionsRef.current.contains(e.target as Node)
      ) {
        setAllSuggestionsOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const handleTabSwitch = useCallback(
    (tab: MainTab) => {
      if (tab === mainTab) return;
      void queryClient.invalidateQueries({ queryKey: ["zones-count"] });
      void queryClient.invalidateQueries({ queryKey: ["insight-my-zones"] });
      void queryClient.invalidateQueries({ queryKey: ["insight-all-zones"] });
      void queryClient.invalidateQueries({
        queryKey: ["insight-abandoned-zones"],
      });
      setTabFading(true);
      setTimeout(() => {
        setMainTab(tab);
        if (tab === "abandonedZones") setAbandonedSubTab("myAbandoned");
        setTimeout(() => setTabFading(false), 40);
      }, 180);
    },
    [mainTab, queryClient],
  );

  const handleAbandonedSubTabSwitch = useCallback(
    (sub: AbandonedSubTab) => {
      if (sub === abandonedSubTab) return;
      void queryClient.invalidateQueries({ queryKey: ["zones-count"] });
      void queryClient.invalidateQueries({ queryKey: ["deleted-zones"] });
      setAbandonedSubTab(sub);
    },
    [abandonedSubTab, queryClient],
  );

  // ── Refresh ────────────────────────────────────────────────────────────────────
  const handleRefresh = useCallback(() => {
    setAccessFilter(null);
    setEmailFilter("");
    setMySuggestionsOpen(false);
    setAllSuggestionsOpen(false);
    setZoneTimeRange("all");
    setZoneDateFrom("");
    setZoneDateTo("");
    void queryClient.invalidateQueries({ queryKey: ["zones-count"] });
    if (mainTab === "myZones") {
      setMyZonesInput("");
      setMyZonesQuery("");
      myZones.resetPaging();
      void queryClient.invalidateQueries({ queryKey: ["zones"] });
    } else if (mainTab === "allZones") {
      setAllZonesInput("");
      setAllZonesQuery("");
      allZones.resetPaging();
      void queryClient.invalidateQueries({ queryKey: ["zones"] });
    } else {
      setAbandonedInput("");
      setAbandonedQuery("");
      setAbanAccessFilter(null);
      setAbanByGroup(false);
      setAbanTimeRange("all");
      setAbanDateFrom("");
      setAbanDateTo("");
      myAbandoned.resetPaging();
      allAbandoned.resetPaging();
      void queryClient.invalidateQueries({ queryKey: ["deleted-zones"] });
    }
  }, [mainTab, myZones, allZones, myAbandoned, allAbandoned, queryClient]);

  // ── Search handlers ───────────────────────────────────────────────────────────
  const handleMyZonesSearch = useCallback(() => {
    setMySuggestionsOpen(false);
    setEmailFilter(myZonesInput);
    if (myZonesByGroup) {
      setMyZonesQuery(myZonesInput);
      setMyZonesSuggestQuery("");
    } else {
      setMyZonesSuggestQuery(myZonesInput);
      setMyZonesQuery(myZonesInput);
    }
    myZones.resetPaging();
  }, [myZonesInput, myZonesByGroup, myZones]);

  const handleAllZonesSearch = useCallback(() => {
    setAllSuggestionsOpen(false);
    setEmailFilter(allZonesInput);
    if (allZonesByGroup) {
      setAllZonesQuery(allZonesInput);
      setAllZonesSuggestQuery("");
    } else {
      setAllZonesSuggestQuery(allZonesInput);
      setAllZonesQuery(allZonesInput);
    }
    allZones.resetPaging();
  }, [allZonesInput, allZonesByGroup, allZones]);

  const handleAbandonedSearch = useCallback(() => {
    if (!abanByGroup && !abandonedInput.includes("@")) {
      setAbandonedQuery(abandonedInput);
    } else {
      setAbandonedQuery("");
    }
    myAbandoned.resetPaging();
    allAbandoned.resetPaging();
  }, [abandonedInput, abanByGroup, myAbandoned, allAbandoned]);

  // ── Zone CRUD ─────────────────────────────────────────────────────────────────
  const handleCreate = (data: Zone) => {
    myZones.createZone(data, { onSuccess: () => setShowConnectForm(false) });
  };

  // Sync search box on tab switch
  useEffect(() => {
    if (mainTab === "myZones") setMyZonesInput(myZonesQuery);
    else if (mainTab === "allZones") setAllZonesInput(allZonesQuery);
    else setAbandonedInput(abandonedQuery);
  }, [mainTab]);

  const isLoadingCurrent =
    mainTab === "myZones"
      ? myZones.isLoading || myZones.isFetching
      : mainTab === "allZones"
        ? allZones.isLoading || allZones.isFetching
        : activeAbandonedHook.isLoading || activeAbandonedHook.isFetching;

  return (
    <div>
      {/* ── Page header ── */}
      <div className="rounded-3 mb-4 d-flex justify-content-between align-items-center vds-page-header">
        <div className="d-flex align-items-center gap-3">
          <div className="rounded-3 d-flex align-items-center justify-content-center vds-page-header__icon">
            <i className="bi bi-diagram-3-fill text-white fs-5" />
          </div>
          <div>
            <h4 className="mb-0 fw-bold vds-page-header__title">Zones</h4>
            <small className="text-muted">
              Manage DNS zones and their configurations
            </small>
          </div>
        </div>
        {mainTab === "myZones" && isSuper && (
          <button
            className="btn btn-primary d-flex align-items-center gap-2 vds-btn-primary-shadow vds-btn-nav"
            onClick={() => setShowConnectForm((p) => !p)}
          >
            <i className="bi bi-plug-fill" />
            Connect Zone
          </button>
        )}
      </div>

      {/* ── Connect Zone modal ── */}
      {showConnectForm && (
        <>
          <div
            className="modal fade show d-block"
            tabIndex={-1}
            role="dialog"
            onClick={(e) => {
              if (e.target === e.currentTarget) setShowConnectForm(false);
            }}
          >
            <div
              className="modal-dialog modal-xl modal-dialog-centered modal-dialog-scrollable"
              role="document"
            >
              <div className="modal-content">
                <div
                  className="modal-header"
                  style={{
                    background: "linear-gradient(90deg, #1e5fa8, #0d1b3e)",
                    color: "#fff",
                  }}
                >
                  <h5 className="modal-title d-flex align-items-center gap-2">
                    <i className="bi bi-plug-fill" />
                    Connect to Zone
                  </h5>
                  <button
                    type="button"
                    className="btn-close btn-close-white"
                    onClick={() => setShowConnectForm(false)}
                  />
                </div>
                <div className="modal-body">
                  <ZoneForm
                    groups={groupsData ?? []}
                    backendIds={backendIds ?? []}
                    onSubmit={handleCreate}
                    onCancel={() => setShowConnectForm(false)}
                    isSubmitting={myZones.isCreating}
                    mode="create"
                  />
                </div>
              </div>
            </div>
          </div>
          <div
            className="modal-backdrop fade show"
            style={{
              backdropFilter: "blur(4px)",
              WebkitBackdropFilter: "blur(4px)",
              opacity: 0.7,
            }}
          />
        </>
      )}

      {/* ── Toolbar ── */}
      <div className="card mb-2 vds-toolbar-card">
        <div className="card-body py-2 px-3">
          <div className="d-flex align-items-center gap-2">
            {/* Tab pills */}
            <div className="vds-pill-toggle">
              <button
                type="button"
                className={`vds-pill-toggle__btn${mainTab === "myZones" ? " vds-pill-toggle__btn--active" : ""}`}
                onClick={() => handleTabSwitch("myZones")}
              >
                <i className="bi bi-person-check" />
                My Zones
              </button>
              <button
                type="button"
                className={`vds-pill-toggle__btn${mainTab === "allZones" ? " vds-pill-toggle__btn--active" : ""}`}
                onClick={() => handleTabSwitch("allZones")}
              >
                <i className="bi bi-globe" />
                All Zones
              </button>
              <button
                type="button"
                className={`vds-pill-toggle__btn${mainTab === "abandonedZones" ? " vds-pill-toggle__btn--active" : ""}`}
                onClick={() => handleTabSwitch("abandonedZones")}
              >
                <i className="bi bi-trash3" />
                Abandoned Zones
              </button>
            </div>

            <div className="ms-auto d-flex align-items-center gap-2">
              <button
                className="vds-cards-toggle-btn"
                onClick={() => setShowCards((v) => !v)}
              >
                <span className="vds-cards-toggle-btn__icon">
                  <i
                    className={`bi ${showCards ? "bi-grid-fill" : "bi-grid"}`}
                  />
                </span>
                <span>{showCards ? "Hide Cards" : "Show Cards"}</span>
                <span
                  className={`vds-cards-toggle-btn__dot${showCards ? "" : " vds-cards-toggle-btn__dot--off"}`}
                />
              </button>
              <button
                type="button"
                className="vds-cards-toggle-btn"
                onClick={() => setShowFilters((v) => !v)}
              >
                <span className="vds-cards-toggle-btn__icon">
                  <i
                    className={`bi ${showFilters ? "bi-x-lg" : "bi-sliders"}`}
                  />
                </span>
                <span>{showFilters ? "Hide Filters" : "Show Filters"}</span>
                <span
                  className={`vds-cards-toggle-btn__dot${showFilters ? "" : " vds-cards-toggle-btn__dot--off"}`}
                />
              </button>
              <button
                type="button"
                className="btn btn-sm vds-btn-flat d-flex align-items-center justify-content-center"
                style={{
                  width: 32,
                  height: 32,
                  padding: 0,
                  flexShrink: 0,
                  borderRadius: "50%",
                }}
                title="Refresh"
                onClick={handleRefresh}
              >
                <i
                  className="bi bi-arrow-clockwise"
                  style={{ fontSize: "1rem" }}
                />
              </button>
            </div>
          </div>
          {/* ── Filters row (animated) + abandoned subtab always-visible ── */}
          <div
            className="d-flex align-items-center pt-2"
            style={{ minHeight: 32 }}
          >
            {/* Abandoned subtab: always visible on the left */}
            {mainTab === "abandonedZones" && (
              <div className="vds-pill-toggle me-2" style={{ flexShrink: 0 }}>
                <button
                  type="button"
                  className={`vds-pill-toggle__btn${abandonedSubTab === "myAbandoned" ? " vds-pill-toggle__btn--active" : ""}`}
                  onClick={() => handleAbandonedSubTabSwitch("myAbandoned")}
                >
                  <i className="bi bi-person-check" />
                  My Zones
                </button>
                <button
                  type="button"
                  className={`vds-pill-toggle__btn${abandonedSubTab === "allAbandoned" ? " vds-pill-toggle__btn--active" : ""}`}
                  onClick={() => handleAbandonedSubTabSwitch("allAbandoned")}
                >
                  <i className="bi bi-globe" />
                  All Zones
                </button>
              </div>
            )}
            {/* Animated filters — always takes remaining space, collapses on hide */}
            <div
              className="ms-auto"
              style={{
                maxHeight: showFilters ? "60px" : "0px",
                opacity: showFilters ? 1 : 0,
                overflow: showFilters ? "visible" : "hidden",
                transition:
                  "max-height 0.4s cubic-bezier(0.4,0,0.2,1), opacity 0.3s ease",
              }}
            >
              <div
                className="d-flex align-items-center justify-content-end gap-2"
                style={{ whiteSpace: "nowrap" }}
              >
                {/* My Zones search + filters */}
                {mainTab === "myZones" && (
                  <>
                    <div
                      ref={mySuggestionsRef}
                      className="position-relative"
                      style={{ width: 220, flexShrink: 1 }}
                    >
                      <div className="vds-search-group input-group input-group-sm">
                        <span className="input-group-text border-0 bg-transparent pe-1">
                          <i className="bi bi-search text-muted" />
                        </span>
                        <input
                          type="text"
                          className="form-control border-0 ps-0 shadow-none bg-transparent"
                          placeholder={
                            myZonesByGroup
                              ? "Search by admin group name"
                              : "Search zones by name"
                          }
                          value={myZonesInput}
                          autoComplete="off"
                          onFocus={() => {
                            if (myZonesInput.length > 0)
                              setMySuggestionsOpen(true);
                          }}
                          onChange={(e) => {
                            const val = e.target.value;
                            setMyZonesInput(val);
                            setMySuggestionsOpen(val.length > 0);
                            if (val === "") {
                              setEmailFilter("");
                              setMyZonesQuery("");
                              myZones.resetPaging();
                            }
                          }}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              setMySuggestionsOpen(false);
                              handleMyZonesSearch();
                            }
                            if (e.key === "Escape") setMySuggestionsOpen(false);
                          }}
                        />
                      </div>
                      {mySuggestionsOpen &&
                        (() => {
                          if (myZonesByGroup) {
                            const groupNames = (groupsData ?? [])
                              .map((g) => g.name)
                              .filter(
                                (n): n is string =>
                                  !!n &&
                                  n
                                    .toLowerCase()
                                    .includes(myZonesInput.toLowerCase()),
                              )
                              .slice(0, 10);
                            if (groupNames.length === 0) return null;
                            return (
                              <ul
                                className="vds-suggestions-list list-group position-absolute"
                                style={{
                                  left: 0,
                                  minWidth: "100%",
                                  width: "max-content",
                                }}
                              >
                                {groupNames.map((name) => (
                                  <li
                                    key={name}
                                    className="list-group-item list-group-item-action d-flex align-items-center gap-2 vds-suggestion-item"
                                    onMouseDown={(e) => {
                                      e.preventDefault();
                                      setMyZonesInput(name);
                                      setEmailFilter(name);
                                      setMyZonesQuery(name);
                                      setMySuggestionsOpen(false);
                                      myZones.resetPaging();
                                    }}
                                  >
                                    <i
                                      className="bi bi-people text-muted"
                                      style={{
                                        fontSize: "0.75rem",
                                        flexShrink: 0,
                                      }}
                                    />
                                    <span
                                      style={{
                                        flex: 1,
                                        minWidth: 0,
                                        overflow: "hidden",
                                        textOverflow: "ellipsis",
                                        whiteSpace: "nowrap",
                                      }}
                                    >
                                      {name}
                                    </span>
                                  </li>
                                ))}
                              </ul>
                            );
                          }
                          if (mySuggestions.length === 0) return null;
                          return (
                            <ul
                              className="vds-suggestions-list list-group position-absolute"
                              style={{
                                left: 0,
                                minWidth: "100%",
                                width: "max-content",
                              }}
                            >
                              {mySuggestions.slice(0, 10).map((z) => (
                                <li
                                  key={z.id}
                                  className="list-group-item list-group-item-action d-flex align-items-center gap-2 vds-suggestion-item"
                                  onMouseDown={(e) => {
                                    e.preventDefault();
                                    setMyZonesInput(z.name);
                                    setEmailFilter(z.name);
                                    setMyZonesQuery(z.name);
                                    setMySuggestionsOpen(false);
                                    myZones.resetPaging();
                                  }}
                                >
                                  <i
                                    className="bi bi-diagram-3 text-muted"
                                    style={{
                                      fontSize: "0.75rem",
                                      flexShrink: 0,
                                    }}
                                  />
                                  <span
                                    style={{
                                      flex: 1,
                                      minWidth: 0,
                                      overflow: "hidden",
                                      textOverflow: "ellipsis",
                                      whiteSpace: "nowrap",
                                    }}
                                  >
                                    {z.name}
                                  </span>
                                </li>
                              ))}
                            </ul>
                          );
                        })()}
                    </div>

                    {/* By Admin Group toggle */}
                    <button
                      className={`btn btn-sm d-flex align-items-center gap-1 vds-btn-flat${myZonesByGroup ? " vds-btn-flat--active" : ""}`}
                      onClick={() => {
                        setMyZonesByGroup((v) => !v);
                        setMySuggestionsOpen(false);
                        setMyZonesInput("");
                        setMyZonesQuery("");
                        setMyZonesSuggestQuery("");
                        myZones.resetPaging();
                      }}
                    >
                      <i className="bi bi-people" />
                      <span className="vds-btn-flat__label">
                        By Admin Group
                      </span>
                      {myZonesByGroup && (
                        <span className="vds-filter-chip--accent">On</span>
                      )}
                    </button>

                    {/* Hide PTR toggle */}
                    <button
                      className={`btn btn-sm d-flex align-items-center gap-1 vds-btn-flat${myZonesHidePtr ? " vds-btn-flat--active" : ""}`}
                      onClick={() => {
                        setMyZonesHidePtr((v) => !v);
                        myZones.resetPaging();
                      }}
                    >
                      <i className="bi bi-arrow-left-right" />
                      <span className="vds-btn-flat__label">Hide PTR</span>
                      {myZonesHidePtr && (
                        <span className="vds-filter-chip--accent">On</span>
                      )}
                    </button>

                    {/* Access filter dropdown */}
                    <div ref={accessDropdownRef} className="position-relative">
                      <button
                        className="btn btn-sm d-flex align-items-center gap-1 vds-btn-flat"
                        onClick={() => setAccessDropdownOpen((o) => !o)}
                      >
                        <i className="bi bi-shield-lock" />
                        <span className="vds-btn-flat__label">Access</span>
                        {accessFilter && (
                          <span className="vds-filter-chip--accent">
                            {accessFilter === "shared" ? "Shared" : "Private"}
                          </span>
                        )}
                        <i
                          className={`bi bi-chevron-${accessDropdownOpen ? "up" : "down"} ms-1`}
                          style={{ fontSize: "0.65rem", color: "#506080" }}
                        />
                      </button>
                      {accessDropdownOpen && (hasShared || hasPrivate) && (
                        <ul
                          className="list-group position-absolute shadow"
                          style={{
                            zIndex: 1050,
                            top: "calc(100% + 4px)",
                            right: 0,
                            minWidth: "140px",
                            borderRadius: "0.55rem",
                            overflow: "hidden",
                            border: "1px solid #d4dbe8",
                          }}
                        >
                          {hasShared && (
                            <li
                              className={`list-group-item list-group-item-action py-2 px-3 d-flex align-items-center gap-2 vds-suggestion-item${accessFilter === "shared" ? " vds-role-item--selected" : ""}`}
                              style={{ cursor: "pointer", fontSize: "0.85rem" }}
                              onMouseDown={() => {
                                setAccessFilter(
                                  accessFilter === "shared" ? null : "shared",
                                );
                                setAccessDropdownOpen(false);
                              }}
                            >
                              <i className="bi bi-share-fill" /> Shared
                            </li>
                          )}
                          {hasPrivate && (
                            <li
                              className={`list-group-item list-group-item-action py-2 px-3 d-flex align-items-center gap-2 vds-suggestion-item${accessFilter === "private" ? " vds-role-item--selected" : ""}`}
                              style={{ cursor: "pointer", fontSize: "0.85rem" }}
                              onMouseDown={() => {
                                setAccessFilter(
                                  accessFilter === "private" ? null : "private",
                                );
                                setAccessDropdownOpen(false);
                              }}
                            >
                              <i className="bi bi-lock-fill text-secondary" />{" "}
                              Private
                            </li>
                          )}
                        </ul>
                      )}
                    </div>
                  </>
                )}

                {/* All Zones search + filters */}
                {mainTab === "allZones" && (
                  <>
                    <div
                      ref={allSuggestionsRef}
                      className="position-relative"
                      style={{ width: 220, flexShrink: 1 }}
                    >
                      <div className="vds-search-group input-group input-group-sm">
                        <span className="input-group-text border-0 bg-transparent pe-1">
                          <i className="bi bi-search text-muted" />
                        </span>
                        <input
                          type="text"
                          className="form-control border-0 ps-0 shadow-none bg-transparent"
                          placeholder={
                            allZonesByGroup
                              ? "Search by admin group name"
                              : "Search zones by name"
                          }
                          value={allZonesInput}
                          autoComplete="off"
                          onFocus={() => {
                            if (allZonesInput.length > 0)
                              setAllSuggestionsOpen(true);
                          }}
                          onChange={(e) => {
                            const val = e.target.value;
                            setAllZonesInput(val);
                            setAllSuggestionsOpen(val.length > 0);
                            if (val === "") {
                              setEmailFilter("");
                              setAllZonesQuery("");
                              allZones.resetPaging();
                            }
                          }}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              setAllSuggestionsOpen(false);
                              handleAllZonesSearch();
                            }
                            if (e.key === "Escape")
                              setAllSuggestionsOpen(false);
                          }}
                        />
                      </div>
                      {allSuggestionsOpen &&
                        (() => {
                          if (allZonesByGroup) {
                            const groupNames = (groupsData ?? [])
                              .map((g) => g.name)
                              .filter(
                                (n): n is string =>
                                  !!n &&
                                  n
                                    .toLowerCase()
                                    .includes(allZonesInput.toLowerCase()),
                              )
                              .slice(0, 10);
                            if (groupNames.length === 0) return null;
                            return (
                              <ul
                                className="vds-suggestions-list list-group position-absolute"
                                style={{
                                  left: 0,
                                  minWidth: "100%",
                                  width: "max-content",
                                }}
                              >
                                {groupNames.map((name) => (
                                  <li
                                    key={name}
                                    className="list-group-item list-group-item-action d-flex align-items-center gap-2 vds-suggestion-item"
                                    onMouseDown={(e) => {
                                      e.preventDefault();
                                      setAllZonesInput(name);
                                      setEmailFilter(name);
                                      setAllZonesQuery(name);
                                      setAllSuggestionsOpen(false);
                                      allZones.resetPaging();
                                    }}
                                  >
                                    <i
                                      className="bi bi-people text-muted"
                                      style={{
                                        fontSize: "0.75rem",
                                        flexShrink: 0,
                                      }}
                                    />
                                    <span
                                      style={{
                                        flex: 1,
                                        minWidth: 0,
                                        overflow: "hidden",
                                        textOverflow: "ellipsis",
                                        whiteSpace: "nowrap",
                                      }}
                                    >
                                      {name}
                                    </span>
                                  </li>
                                ))}
                              </ul>
                            );
                          }
                          if (allSuggestions.length === 0) return null;
                          return (
                            <ul
                              className="vds-suggestions-list list-group position-absolute"
                              style={{
                                left: 0,
                                minWidth: "100%",
                                width: "max-content",
                              }}
                            >
                              {allSuggestions.slice(0, 10).map((z) => (
                                <li
                                  key={z.id}
                                  className="list-group-item list-group-item-action d-flex align-items-center gap-2 vds-suggestion-item"
                                  onMouseDown={(e) => {
                                    e.preventDefault();
                                    setAllZonesInput(z.name);
                                    setEmailFilter(z.name);
                                    setAllZonesQuery(z.name);
                                    setAllSuggestionsOpen(false);
                                    allZones.resetPaging();
                                  }}
                                >
                                  <i
                                    className="bi bi-diagram-3 text-muted"
                                    style={{
                                      fontSize: "0.75rem",
                                      flexShrink: 0,
                                    }}
                                  />
                                  <span
                                    style={{
                                      flex: 1,
                                      minWidth: 0,
                                      overflow: "hidden",
                                      textOverflow: "ellipsis",
                                      whiteSpace: "nowrap",
                                    }}
                                  >
                                    {z.name}
                                  </span>
                                </li>
                              ))}
                            </ul>
                          );
                        })()}
                    </div>

                    {/* By Admin Group toggle */}
                    <button
                      className={`btn btn-sm d-flex align-items-center gap-1 vds-btn-flat${allZonesByGroup ? " vds-btn-flat--active" : ""}`}
                      onClick={() => {
                        setAllZonesByGroup((v) => !v);
                        setAllSuggestionsOpen(false);
                        setAllZonesInput("");
                        setAllZonesQuery("");
                        setAllZonesSuggestQuery("");
                        allZones.resetPaging();
                      }}
                    >
                      <i className="bi bi-people" />
                      <span className="vds-btn-flat__label">
                        By Admin Group
                      </span>
                      {allZonesByGroup && (
                        <span className="vds-filter-chip--accent">On</span>
                      )}
                    </button>

                    {/* Hide PTR toggle */}
                    <button
                      className={`btn btn-sm d-flex align-items-center gap-1 vds-btn-flat${allZonesHidePtr ? " vds-btn-flat--active" : ""}`}
                      onClick={() => {
                        setAllZonesHidePtr((v) => !v);
                        allZones.resetPaging();
                      }}
                    >
                      <i className="bi bi-arrow-left-right" />
                      <span className="vds-btn-flat__label">Hide PTR</span>
                      {allZonesHidePtr && (
                        <span className="vds-filter-chip--accent">On</span>
                      )}
                    </button>

                    {/* Access filter dropdown */}
                    <div ref={accessDropdownRef} className="position-relative">
                      <button
                        className="btn btn-sm d-flex align-items-center gap-1 vds-btn-flat"
                        onClick={() => setAccessDropdownOpen((o) => !o)}
                      >
                        <i className="bi bi-shield-lock" />
                        <span className="vds-btn-flat__label">Access</span>
                        {accessFilter && (
                          <span className="vds-filter-chip--accent">
                            {accessFilter === "shared" ? "Shared" : "Private"}
                          </span>
                        )}
                        <i
                          className={`bi bi-chevron-${accessDropdownOpen ? "up" : "down"} ms-1`}
                          style={{ fontSize: "0.65rem", color: "#506080" }}
                        />
                      </button>
                      {accessDropdownOpen && (hasShared || hasPrivate) && (
                        <ul
                          className="list-group position-absolute shadow"
                          style={{
                            zIndex: 1050,
                            top: "calc(100% + 4px)",
                            right: 0,
                            minWidth: "140px",
                            borderRadius: "0.55rem",
                            overflow: "hidden",
                            border: "1px solid #d4dbe8",
                          }}
                        >
                          {hasShared && (
                            <li
                              className={`list-group-item list-group-item-action py-2 px-3 d-flex align-items-center gap-2 vds-suggestion-item${accessFilter === "shared" ? " vds-role-item--selected" : ""}`}
                              style={{ cursor: "pointer", fontSize: "0.85rem" }}
                              onMouseDown={() => {
                                setAccessFilter(
                                  accessFilter === "shared" ? null : "shared",
                                );
                                setAccessDropdownOpen(false);
                              }}
                            >
                              <i className="bi bi-share-fill" /> Shared
                            </li>
                          )}
                          {hasPrivate && (
                            <li
                              className={`list-group-item list-group-item-action py-2 px-3 d-flex align-items-center gap-2 vds-suggestion-item${accessFilter === "private" ? " vds-role-item--selected" : ""}`}
                              style={{ cursor: "pointer", fontSize: "0.85rem" }}
                              onMouseDown={() => {
                                setAccessFilter(
                                  accessFilter === "private" ? null : "private",
                                );
                                setAccessDropdownOpen(false);
                              }}
                            >
                              <i className="bi bi-lock-fill text-secondary" />{" "}
                              Private
                            </li>
                          )}
                        </ul>
                      )}
                    </div>
                  </>
                )}

                {/* Abandoned Zones search */}
                {mainTab === "abandonedZones" && (
                  <div
                    className="vds-search-group input-group input-group-sm"
                    style={{ width: 220, flexShrink: 1 }}
                  >
                    <span className="input-group-text border-0 bg-transparent pe-1">
                      <i className="bi bi-search text-muted" />
                    </span>
                    <input
                      type="text"
                      className="form-control border-0 ps-0 shadow-none bg-transparent"
                      placeholder={
                        abanByGroup
                          ? "Search by admin group name"
                          : "Search by zone name"
                      }
                      value={abandonedInput}
                      autoComplete="off"
                      onChange={(e) => {
                        const val = e.target.value;
                        setAbandonedInput(val);
                        if (val === "") {
                          setAbandonedQuery("");
                          myAbandoned.resetPaging();
                          allAbandoned.resetPaging();
                        }
                      }}
                      onKeyDown={(e) =>
                        e.key === "Enter" && handleAbandonedSearch()
                      }
                    />
                  </div>
                )}

                {/* Abandoned Zones: inline filters after search */}
                {mainTab === "abandonedZones" && (
                  <>
                    {/* By Admin Group toggle */}
                    <button
                      className={`btn btn-sm d-flex align-items-center gap-1 vds-btn-flat${abanByGroup ? " vds-btn-flat--active" : ""}`}
                      onClick={() => {
                        setAbanByGroup((v) => !v);
                        setAbandonedInput("");
                      }}
                    >
                      <i className="bi bi-people" />
                      <span className="vds-btn-flat__label">
                        By Admin Group
                      </span>
                      {abanByGroup && (
                        <span className="vds-filter-chip--accent">On</span>
                      )}
                    </button>

                    {/* Access filter dropdown */}
                    <div
                      ref={abanAccessDropdownRef}
                      className="position-relative"
                    >
                      <button
                        className="btn btn-sm d-flex align-items-center gap-1 vds-btn-flat"
                        onClick={() => setAbanAccessDropdownOpen((o) => !o)}
                      >
                        <i className="bi bi-shield-lock" />
                        <span className="vds-btn-flat__label">Access</span>
                        {abanAccessFilter && (
                          <span className="vds-filter-chip--accent">
                            {abanAccessFilter === "shared"
                              ? "Shared"
                              : "Private"}
                          </span>
                        )}
                        <i
                          className={`bi bi-chevron-${abanAccessDropdownOpen ? "up" : "down"} ms-1`}
                          style={{ fontSize: "0.65rem", color: "#506080" }}
                        />
                      </button>
                      {abanAccessDropdownOpen &&
                        (abanHasShared || abanHasPrivate) && (
                          <ul
                            className="list-group position-absolute shadow"
                            style={{
                              zIndex: 1050,
                              top: "calc(100% + 4px)",
                              right: 0,
                              minWidth: "140px",
                              borderRadius: "0.55rem",
                              overflow: "hidden",
                              border: "1px solid #d4dbe8",
                            }}
                          >
                            {abanHasShared && (
                              <li
                                className={`list-group-item list-group-item-action py-2 px-3 d-flex align-items-center gap-2 vds-suggestion-item${abanAccessFilter === "shared" ? " vds-role-item--selected" : ""}`}
                                style={{
                                  cursor: "pointer",
                                  fontSize: "0.85rem",
                                }}
                                onMouseDown={() => {
                                  setAbanAccessFilter(
                                    abanAccessFilter === "shared"
                                      ? null
                                      : "shared",
                                  );
                                  setAbanAccessDropdownOpen(false);
                                }}
                              >
                                <i className="bi bi-share-fill" /> Shared
                              </li>
                            )}
                            {abanHasPrivate && (
                              <li
                                className={`list-group-item list-group-item-action py-2 px-3 d-flex align-items-center gap-2 vds-suggestion-item${abanAccessFilter === "private" ? " vds-role-item--selected" : ""}`}
                                style={{
                                  cursor: "pointer",
                                  fontSize: "0.85rem",
                                }}
                                onMouseDown={() => {
                                  setAbanAccessFilter(
                                    abanAccessFilter === "private"
                                      ? null
                                      : "private",
                                  );
                                  setAbanAccessDropdownOpen(false);
                                }}
                              >
                                <i className="bi bi-lock-fill text-secondary" />{" "}
                                Private
                              </li>
                            )}
                          </ul>
                        )}
                    </div>
                  </>
                )}
                {/* TIME FILTER for My/All Zones */}
                {(mainTab === "myZones" || mainTab === "allZones") && (
                  <TimeFilterDropdown
                    value={zoneTimeRange}
                    dateFrom={zoneDateFrom}
                    dateTo={zoneDateTo}
                    onChange={setZoneTimeRange}
                    onDateFromChange={setZoneDateFrom}
                    onDateToChange={setZoneDateTo}
                  />
                )}
                {/* TIME FILTER for Abandoned Zones */}
                {mainTab === "abandonedZones" && (
                  <TimeFilterDropdown
                    value={abanTimeRange}
                    dateFrom={abanDateFrom}
                    dateTo={abanDateTo}
                    onChange={setAbanTimeRange}
                    onDateFromChange={setAbanDateFrom}
                    onDateToChange={setAbanDateTo}
                  />
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ── Active filter tags — My/All Zones ── */}
      {(mainTab === "myZones" || mainTab === "allZones") &&
        (emailFilter || accessFilter || zoneTimeRange !== "all") && (
          <div className="d-flex justify-content-end gap-2 mb-2 flex-wrap align-items-center px-1">
            {emailFilter && (
              <span className="vds-active-filter-tag d-flex align-items-center gap-1">
                <i
                  className={byGroupActive ? "bi bi-people" : "bi bi-search"}
                />
                {byGroupActive ? "Admin Group" : "Search"}: {emailFilter}
                <button
                  type="button"
                  className="btn-close ms-1"
                  style={{
                    fontSize: "0.5rem",
                    filter:
                      "invert(30%) sepia(80%) saturate(500%) hue-rotate(190deg)",
                  }}
                  onClick={() => {
                    setEmailFilter("");
                    if (mainTab === "myZones") {
                      setMyZonesInput("");
                      setMyZonesQuery("");
                      myZones.resetPaging();
                    } else {
                      setAllZonesInput("");
                      setAllZonesQuery("");
                      allZones.resetPaging();
                    }
                  }}
                />
              </span>
            )}
            {accessFilter && (
              <span className="vds-active-filter-tag d-flex align-items-center gap-1">
                <i className="bi bi-shield-lock" />
                Access: {accessFilter === "shared" ? "Shared" : "Private"}
                <button
                  type="button"
                  className="btn-close ms-1"
                  style={{
                    fontSize: "0.5rem",
                    filter:
                      "invert(30%) sepia(80%) saturate(500%) hue-rotate(190deg)",
                  }}
                  onClick={() => setAccessFilter(null)}
                />
              </span>
            )}
            {zoneTimeRange !== "all" && (
              <span className="vds-active-filter-tag d-flex align-items-center gap-1">
                <i className="bi bi-calendar3" />
                Last Sync:{" "}
                {zoneTimeRange === "1d"
                  ? "Today"
                  : zoneTimeRange === "7d"
                    ? "Last 7 days"
                    : zoneTimeRange === "30d"
                      ? "Last 30 days"
                      : zoneTimeRange === "90d"
                        ? "Last 90 days"
                        : `${zoneDateFrom || "…"} – ${zoneDateTo || "…"}`}
                <button
                  type="button"
                  className="btn-close ms-1"
                  style={{
                    fontSize: "0.5rem",
                    filter:
                      "invert(30%) sepia(80%) saturate(500%) hue-rotate(190deg)",
                  }}
                  onClick={() => {
                    setZoneTimeRange("all");
                    setZoneDateFrom("");
                    setZoneDateTo("");
                  }}
                />
              </span>
            )}
            <button
              type="button"
              className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
              style={{
                color: "#e53e3e",
                border: "1px solid rgba(229,62,62,0.3)",
                fontSize: "0.78rem",
              }}
              onClick={() => {
                setEmailFilter("");
                setAccessFilter(null);
                setZoneTimeRange("all");
                setZoneDateFrom("");
                setZoneDateTo("");
                if (mainTab === "myZones") {
                  setMyZonesInput("");
                  setMyZonesQuery("");
                  myZones.resetPaging();
                } else {
                  setAllZonesInput("");
                  setAllZonesQuery("");
                  allZones.resetPaging();
                }
              }}
            >
              <i className="bi bi-x-circle" />
              Clear All
            </button>
          </div>
        )}

      {/* ── Active filter tags — Abandoned Zones ── */}
      {mainTab === "abandonedZones" && anyAbandonedFilterActive && (
        <div className="d-flex justify-content-end gap-2 mb-2 flex-wrap align-items-center px-1">
          {abandonedInput && (
            <span className="vds-active-filter-tag d-flex align-items-center gap-1">
              <i className={abanByGroup ? "bi bi-people" : "bi bi-search"} />
              {abanByGroup ? "Admin Group" : "Search"}: {abandonedInput}
              <button
                type="button"
                className="btn-close ms-1"
                style={{
                  fontSize: "0.5rem",
                  filter:
                    "invert(30%) sepia(80%) saturate(500%) hue-rotate(190deg)",
                }}
                onClick={() => {
                  setAbandonedInput("");
                  setAbandonedQuery("");
                  myAbandoned.resetPaging();
                  allAbandoned.resetPaging();
                }}
              />
            </span>
          )}
          {abanByGroup && !abandonedInput && (
            <span className="vds-active-filter-tag d-flex align-items-center gap-1">
              <i className="bi bi-people" />
              By Admin Group
              <button
                type="button"
                className="btn-close ms-1"
                style={{
                  fontSize: "0.5rem",
                  filter:
                    "invert(30%) sepia(80%) saturate(500%) hue-rotate(190deg)",
                }}
                onClick={() => setAbanByGroup(false)}
              />
            </span>
          )}
          {abanAccessFilter && (
            <span className="vds-active-filter-tag d-flex align-items-center gap-1">
              <i className="bi bi-shield-lock" />
              Access: {abanAccessFilter === "shared" ? "Shared" : "Private"}
              <button
                type="button"
                className="btn-close ms-1"
                style={{
                  fontSize: "0.5rem",
                  filter:
                    "invert(30%) sepia(80%) saturate(500%) hue-rotate(190deg)",
                }}
                onClick={() => setAbanAccessFilter(null)}
              />
            </span>
          )}
          {abanTimeRange !== "all" && (
            <span className="vds-active-filter-tag d-flex align-items-center gap-1">
              <i className="bi bi-calendar3" />
              Abandoned:{" "}
              {abanTimeRange === "1d"
                ? "Today"
                : abanTimeRange === "7d"
                  ? "Last 7 days"
                  : abanTimeRange === "30d"
                    ? "Last 30 days"
                    : abanTimeRange === "90d"
                      ? "Last 90 days"
                      : `${abanDateFrom || "…"} – ${abanDateTo || "…"}`}
              <button
                type="button"
                className="btn-close ms-1"
                style={{
                  fontSize: "0.5rem",
                  filter:
                    "invert(30%) sepia(80%) saturate(500%) hue-rotate(190deg)",
                }}
                onClick={() => {
                  setAbanTimeRange("all");
                  setAbanDateFrom("");
                  setAbanDateTo("");
                }}
              />
            </span>
          )}
          <button
            type="button"
            className="btn btn-sm vds-btn-flat d-flex align-items-center gap-1"
            style={{
              color: "#e53e3e",
              border: "1px solid rgba(229,62,62,0.3)",
              fontSize: "0.78rem",
            }}
            onClick={() => {
              setAbandonedInput("");
              setAbandonedQuery("");
              setAbanByGroup(false);
              setAbanAccessFilter(null);
              setAbanTimeRange("all");
              setAbanDateFrom("");
              setAbanDateTo("");
              myAbandoned.resetPaging();
              allAbandoned.resetPaging();
            }}
          >
            <i className="bi bi-x-circle" />
            Clear All
          </button>
        </div>
      )}

      {/* ── Insight cards + content (fades on tab switch) ── */}
      <div
        className={`vds-tab-content${tabFading ? " vds-tab-content--fading" : ""}`}
      >
        {/* ── Insight cards ── */}
        {showCards && (
          <div className="row g-2 mb-3 align-items-stretch">
            {/* ── Card 1: Total Zones ── */}
            <div
              className={`${isAbandonedTab ? "col-6 col-md-4" : "col-6 col-md-3"} d-flex`}
            >
              <div className="rounded-3 px-3 py-1 w-100 d-flex flex-column vds-insight-card vds-insight-card--blue">
                <div className="d-flex align-items-center gap-2 mb-1">
                  <div className="rounded-2 vds-insight-icon vds-insight-icon--blue">
                    <i className="bi bi-globe2" />
                  </div>
                  <span className="vds-insight-label vds-insight-label--blue">
                    Total Zones
                    <span className="vds-card-ctx-chip vds-card-ctx-chip--blue ms-1">
                      {cardContextLabel}
                      {cardFiltered ? " ·" : ""}
                    </span>
                  </span>
                  <span className="vds-insight-value vds-insight-value--blue">
                    {cardLoading
                      ? skeletonBlue
                      : cardTotal > 0 || anyFilterNow
                        ? cardTotal
                        : null}
                  </span>
                </div>
                {/* In-view / platform ratio bar */}
                {!cardLoading &&
                  cardTotal > 0 &&
                  card1RefCount !== null &&
                  card1RefCount > 0 && (
                    <div
                      className="vds-insight-progress vds-insight-progress--blue mb-1"
                      style={{ height: 4 }}
                    >
                      <div
                        className="vds-insight-progress__fill vds-insight-progress__fill--blue"
                        style={{
                          width: `${Math.min(100, Math.round((cardTotal / card1RefCount) * 100))}%`,
                          minWidth: 4,
                        }}
                      />
                    </div>
                  )}
                <div className="vds-insight-body vds-insight-body--blue">
                  <div className="vds-insight-stat-label">{card1ViewLabel}</div>
                  <div className="vds-insight-stat-label vds-insight-stat-label--right">
                    {card1RefLabel}
                  </div>
                  <div className="vds-insight-stat-value vds-insight-stat-value--blue">
                    {cardLoading
                      ? "…"
                      : cardTotal > 0 || anyFilterNow
                        ? cardTotal
                        : "—"}
                  </div>
                  <div className="vds-insight-stat-value vds-insight-stat-value--blue vds-insight-stat-value--right">
                    {cardTotal > 0 || anyFilterNow
                      ? card1RefCount == null
                        ? "…"
                        : card1RefCount
                      : "—"}
                  </div>
                  <div className="vds-insight-footnote">
                    <i className="bi bi-diagram-3 me-1 vds-icon-blue-dim" />
                    {mainTab !== "abandonedZones" ? (
                      cardTotal > 0 || anyFilterNow ? (
                        <>
                          You own {insightMyCount ?? "…"} of{" "}
                          {insightAllCount ?? "…"} platform zones
                        </>
                      ) : (
                        "No zones yet"
                      )
                    ) : isAllAbandonedSubTab ? (
                      <>
                        {cardTotal} matching · {insightAbandonedCount ?? "…"}{" "}
                        total abandoned
                      </>
                    ) : cardTotal > 0 || anyFilterNow ? (
                      <>
                        {cardTotal} matching · {insightAbandonedCount ?? "…"}{" "}
                        total abandoned
                      </>
                    ) : (
                      "No abandoned zones"
                    )}
                  </div>
                  {mainTab !== "abandonedZones" &&
                    (cardTotal > 0 || anyFilterNow) && (
                      <div
                        className="vds-insight-footnote"
                        style={{ marginTop: 2 }}
                      >
                        <i
                          className="bi bi-trash3 me-1"
                          style={{ color: "#e53e3e", opacity: 0.75 }}
                        />
                        Abandoned:{" "}
                        <span style={{ color: "#e53e3e", fontWeight: 700 }}>
                          {(mainTab === "myZones"
                            ? (zonesCount?.myAbandonedCount ??
                              (isSuper || isSupport
                                ? insightAbandonedCount
                                : myAbandoned.deletedZones.length))
                            : insightAbandonedCount) ?? "…"}
                        </span>
                        <span className="ms-1" style={{ fontWeight: 400 }}>
                          zone
                          {(mainTab === "myZones"
                            ? (zonesCount?.myAbandonedCount ??
                              (isSuper || isSupport
                                ? insightAbandonedCount
                                : myAbandoned.deletedZones.length))
                            : insightAbandonedCount) !== 1
                            ? "s"
                            : ""}
                        </span>
                      </div>
                    )}
                </div>
              </div>
            </div>

            {/* ── Card 2: Zone Health — hidden on abandoned tab ── */}
            {!isAbandonedTab && (
              <div className="col-6 col-md-3 d-flex">
                <div className="rounded-3 px-3 py-1 w-100 d-flex flex-column vds-insight-card vds-insight-card--teal">
                  <div className="d-flex align-items-center gap-2 mb-1">
                    <div className="rounded-2 vds-insight-icon vds-insight-icon--teal">
                      <i className="bi bi-check-circle-fill" />
                    </div>
                    <span className="vds-insight-label vds-insight-label--teal">
                      Zone Health
                      <span className="vds-card-ctx-chip vds-card-ctx-chip--teal ms-1">
                        {cardContextLabel}
                        {cardFiltered ? " ·" : ""}
                      </span>
                    </span>
                    <span className="vds-insight-value vds-insight-value--teal">
                      {cardLoading
                        ? skeletonTeal
                        : cardTotal > 0 || anyFilterNow
                          ? displayZoneHealthHeader
                          : null}
                    </span>
                  </div>
                  {!cardLoading &&
                  cardTotal > 0 &&
                  healthBase > 0 &&
                  (cardTotal > 0 || anyFilterNow) ? (
                    <>
                      <div
                        className="vds-insight-access-bar mb-1"
                        style={{ height: 6 }}
                      >
                        <div
                          style={{
                            width: `${healthPct}%`,
                            background:
                              "linear-gradient(90deg,#0ca678,#34d399)",
                            flexShrink: 0,
                            minWidth: healthPct > 0 ? 4 : 0,
                          }}
                        />
                        <div
                          style={{
                            flex: 1,
                            background: "rgba(12,166,120,0.15)",
                          }}
                        />
                      </div>
                      <div
                        style={{
                          fontSize: "0.67rem",
                          color: "#0ca678",
                          fontWeight: 700,
                          marginBottom: 3,
                        }}
                      >
                        {healthPct}%{" "}
                        <span style={{ fontWeight: 400, color: "#8099b8" }}>
                          {`of ${accessFilterOnly ? "page" : cardFiltered ? "filtered" : activeHidePtr ? "non-PTR" : cardContextLabel.toLowerCase()} zones are active`}
                        </span>
                      </div>
                    </>
                  ) : (
                    <div style={{ height: 4 }} />
                  )}
                  <div className="vds-insight-body vds-insight-body--teal">
                    <div className="vds-insight-stat-label">
                      {isAbandonedTab ? "PTR" : "Active"}
                    </div>
                    <div className="vds-insight-stat-label vds-insight-stat-label--right">
                      {isAbandonedTab ? "Non-PTR" : "Syncing"}
                    </div>
                    <div className="vds-insight-stat-value vds-insight-stat-value--teal">
                      {cardLoading
                        ? "…"
                        : isAbandonedTab
                          ? (resolvedAbandonedPtrCount ?? "…")
                          : cardTotal > 0 || anyFilterNow
                            ? displayActiveCount
                            : "—"}
                    </div>
                    <div className="vds-insight-stat-value vds-insight-stat-value--teal vds-insight-stat-value--right">
                      {cardLoading
                        ? "…"
                        : isAbandonedTab
                          ? (resolvedAbandonedNonPtrCount ?? "…")
                          : cardTotal > 0 || anyFilterNow
                            ? displaySyncingCount
                            : "—"}
                    </div>
                    <div className="vds-insight-footnote">
                      <i className="bi bi-activity me-1 vds-icon-teal-dim" />
                      {isAbandonedTab ? (
                        cardLoading ? (
                          "Loading…"
                        ) : (
                          "All abandoned zones accounted for"
                        )
                      ) : !cardLoading && cardIncidentCount > 0 ? (
                        <span style={{ color: "#e53e3e" }}>
                          {cardIncidentCount} zone
                          {cardIncidentCount === 1 ? "" : "s"} need attention
                        </span>
                      ) : cardLoading ? (
                        "Loading…"
                      ) : cardTotal > 0 || anyFilterNow ? (
                        "All zones in view are healthy"
                      ) : (
                        "No zones to evaluate"
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* ── Card 3: Shared & Private ── */}
            <div
              className={`${isAbandonedTab ? "col-6 col-md-4" : "col-6 col-md-3"} d-flex`}
            >
              <div className="rounded-3 px-3 py-1 w-100 d-flex flex-column vds-insight-card vds-insight-card--purple">
                <div className="d-flex align-items-center gap-2 mb-1">
                  <div className="rounded-2 vds-insight-icon vds-insight-icon--purple">
                    <i className="bi bi-shield-lock-fill" />
                  </div>
                  <span className="vds-insight-label vds-insight-label--purple">
                    Access Split
                    <span className="vds-card-ctx-chip vds-card-ctx-chip--purple ms-1">
                      {cardContextLabel}
                      {cardFiltered ? " ·" : ""}
                    </span>
                  </span>
                  <span className="vds-insight-value vds-insight-value--purple">
                    {cardLoading
                      ? skeletonPurple
                      : cardTotal > 0 || anyFilterNow
                        ? cardTotal
                        : null}
                  </span>
                </div>
                {/* Shared / Private ratio bar */}
                {!cardLoading && cardTotal > 0 && accessSplitTotal > 0 && (
                  <div className="vds-insight-access-bar mb-1">
                    <div
                      className="vds-insight-access-bar__shared"
                      style={{
                        width: `${Math.round(((displaySharedCount ?? 0) / accessSplitTotal) * 100)}%`,
                      }}
                    />
                    <div className="vds-insight-access-bar__private" />
                  </div>
                )}
                <div className="vds-insight-body vds-insight-body--purple">
                  <div className="vds-insight-stat-label">
                    <i
                      className="bi bi-share-fill me-1"
                      style={{ fontSize: "0.6rem" }}
                    />
                    Shared
                  </div>
                  <div className="vds-insight-stat-label vds-insight-stat-label--right">
                    <i
                      className="bi bi-lock-fill me-1"
                      style={{ fontSize: "0.6rem" }}
                    />
                    Private
                  </div>
                  <div className="vds-insight-stat-value vds-insight-stat-value--purple">
                    {cardLoading
                      ? "…"
                      : cardTotal > 0 || anyFilterNow
                        ? displaySharedCount
                        : "—"}
                  </div>
                  <div className="vds-insight-stat-value vds-insight-stat-value--purple vds-insight-stat-value--right">
                    {cardLoading
                      ? "…"
                      : cardTotal > 0 || anyFilterNow
                        ? displayPrivateCount
                        : "—"}
                  </div>
                  <div className="vds-insight-footnote">
                    <i className="bi bi-arrow-left-right me-1 vds-icon-purple-dim" />
                    PTR reverse zones:{" "}
                    {cardLoading
                      ? "…"
                      : cardTotal === 0 && !anyFilterNow
                        ? "—"
                        : isAbandonedTab
                          ? (resolvedAbandonedPtrCount ?? cardPtrCount)
                          : activeHidePtr
                            ? 0
                            : accessFilter === "shared"
                              ? myZonesRegularUser
                                ? cardPtrCount
                                : (zonesCount?.sharedPtrCount ?? cardPtrCount)
                              : accessFilter === "private"
                                ? myZonesRegularUser
                                  ? cardPtrCount
                                  : (zonesCount?.privatePtrCount ??
                                    cardPtrCount)
                                : !anyFilterNow && zonesCount != null
                                  ? myZonesRegularUser
                                    ? myPtrServer
                                    : zonesCount.ptrCount
                                  : cardPtrCount}
                  </div>
                </div>
              </div>
            </div>

            {/* ── Card 4: Zone Lifecycle ── */}
            <div
              className={`${isAbandonedTab ? "col-6 col-md-4" : "col-6 col-md-3"} d-flex`}
            >
              <div className="rounded-3 px-3 py-1 w-100 d-flex flex-column vds-insight-card vds-insight-card--amber">
                <div className="d-flex align-items-center gap-2 mb-1">
                  <div className="rounded-2 vds-insight-icon vds-insight-icon--amber">
                    <i className="bi bi-clock-history" />
                  </div>
                  <span className="vds-insight-label vds-insight-label--amber">
                    Zone Lifecycle
                    <span className="vds-card-ctx-chip vds-card-ctx-chip--amber ms-1">
                      {cardContextLabel}
                      {cardFiltered ? " ·" : ""}
                    </span>
                  </span>
                </div>
                {/* Summary strip */}
                <div className="vds-lifecycle-newest">
                  <span className="vds-lifecycle-newest__age">
                    {cardLoading
                      ? "Loading…"
                      : cardSource.length > 0
                        ? `${cardSource.length} zone${cardSource.length === 1 ? "" : "s"} · ${cardActiveCount} active`
                        : "No zones in view"}
                  </span>
                  <span className="vds-lifecycle-newest__meta">
                    {!cardLoading && cardOldestAgeDays !== null
                      ? `Running for ${fmtAge(cardOldestAgeDays)} · ${cardNewThisMonth} added in last 30d`
                      : cardLoading
                        ? ""
                        : "No zones yet"}
                  </span>
                </div>
                {/* Lifecycle stats grid */}
                <div className="vds-lifecycle-grid">
                  <div className="vds-lifecycle-tile">
                    <span
                      className="vds-lifecycle-tile__num"
                      style={
                        !cardLoading && cardNeverSynced > 0
                          ? { color: "#e53e3e" }
                          : undefined
                      }
                    >
                      {cardLoading ? skeletonAmber : cardNeverSynced}
                    </span>
                    <span className="vds-lifecycle-tile__label">
                      Never synced
                    </span>
                  </div>
                  <div className="vds-lifecycle-tile">
                    <span
                      className="vds-lifecycle-tile__num"
                      style={{ color: "#0ca678" }}
                    >
                      {cardLoading ? skeletonAmber : cardRecentlySynced}
                    </span>
                    <span className="vds-lifecycle-tile__label">
                      Synced ≤7d
                    </span>
                  </div>
                  <div className="vds-lifecycle-tile">
                    <span
                      className="vds-lifecycle-tile__num"
                      style={{ color: "#6f42c1" }}
                    >
                      {cardLoading ? skeletonAmber : cardNewThisMonth}
                    </span>
                    <span className="vds-lifecycle-tile__label">
                      New in 30d
                    </span>
                  </div>
                  <div className="vds-lifecycle-tile">
                    <span className="vds-lifecycle-tile__num">
                      {cardLoading
                        ? skeletonAmber
                        : cardOldestAgeDays !== null
                          ? fmtAge(cardOldestAgeDays)
                          : "—"}
                    </span>
                    <span className="vds-lifecycle-tile__label">
                      Oldest zone
                    </span>
                  </div>
                </div>
                <div className="vds-insight-footnote" style={{ marginTop: 4 }}>
                  <i
                    className="bi bi-info-circle me-1"
                    style={{ color: "#b07d2a", opacity: 0.75 }}
                  />
                  <span style={{ color: "#8099b8" }}>
                    Showing zones {pageStart.toLocaleString()}–
                    {pageEnd.toLocaleString()} of {cardTotal.toLocaleString()}
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ── My Zones content ── */}
        {mainTab === "myZones" &&
          (isLoadingCurrent ? (
            <LoadingSpinner />
          ) : (
            <>
              <PaginatedSection
                show={
                  (anyServerCompatibleFilter ||
                    !anyFilterActive ||
                    byGroupSearchActive) &&
                  (myZones.nextPageEnabled || myZones.prevPageEnabled)
                }
                onPrev={myZones.prevPage}
                onNext={myZones.nextPage}
                prevEnabled={myZones.prevPageEnabled}
                nextEnabled={myZones.nextPageEnabled}
                rangeLabel={pageRangeLabel}
              >
                <ZonesTable
                  zones={renderedZones}
                  showAllZones={false}
                  emptyMessage={
                    secondaryFilterActive && renderedZones.length === 0
                      ? "No matching zones on this page"
                      : undefined
                  }
                  emptySubtitle={
                    secondaryFilterActive && renderedZones.length === 0
                      ? "Matching zones may be on a different page — use Next or Previous to find them."
                      : undefined
                  }
                />
              </PaginatedSection>
            </>
          ))}

        {/* ── All Zones content ── */}
        {mainTab === "allZones" &&
          (isLoadingCurrent ? (
            <LoadingSpinner />
          ) : (
            <>
              <PaginatedSection
                show={
                  (anyServerCompatibleFilter ||
                    !anyFilterActive ||
                    byGroupSearchActive) &&
                  (allZones.nextPageEnabled || allZones.prevPageEnabled)
                }
                onPrev={allZones.prevPage}
                onNext={allZones.nextPage}
                prevEnabled={allZones.prevPageEnabled}
                nextEnabled={allZones.nextPageEnabled}
                rangeLabel={pageRangeLabel}
              >
                <ZonesTable
                  zones={renderedZones}
                  showAllZones
                  emptyMessage={
                    secondaryFilterActive && renderedZones.length === 0
                      ? "No matching zones on this page"
                      : undefined
                  }
                  emptySubtitle={
                    secondaryFilterActive && renderedZones.length === 0
                      ? "Matching zones may be on a different page — use Next or Previous to find them."
                      : undefined
                  }
                />
              </PaginatedSection>
            </>
          ))}

        {/* ── Abandoned Zones content ── */}
        {mainTab === "abandonedZones" && (
          <>
            {isLoadingCurrent ? (
              <LoadingSpinner />
            ) : (
              <>
                <PaginatedSection
                  show={
                    (abanAccessFilterOnly || !anyAbandonedFilterActive) &&
                    (activeAbandonedHook.nextPageEnabled ||
                      activeAbandonedHook.prevPageEnabled)
                  }
                  onPrev={activeAbandonedHook.prevPage}
                  onNext={activeAbandonedHook.nextPage}
                  prevEnabled={activeAbandonedHook.prevPageEnabled}
                  nextEnabled={activeAbandonedHook.nextPageEnabled}
                  rangeLabel={pageRangeLabel}
                >
                  <AbandonedZonesTable
                    zones={displayedAbandonedZones}
                    emptyMessage={
                      anyAbandonedFilterActive &&
                      displayedAbandonedZones.length === 0
                        ? "No matching zones on this page"
                        : undefined
                    }
                    emptySubtitle={
                      anyAbandonedFilterActive &&
                      displayedAbandonedZones.length === 0
                        ? "Matching zones may be on a different page — use Next or Previous to find them."
                        : undefined
                    }
                  />
                </PaginatedSection>
              </>
            )}
          </>
        )}
      </div>
      {/* end vds-tab-content */}
    </div>
  );
}
