import os
import re
import sys
import logging
import subprocess
from datetime import datetime
from threading import Lock
from typing import Optional, Tuple, List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, EmailStr, field_validator
import uvicorn

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger(__name__)


class VinylDNSBindDNSManager:
    """Manage VinylDNS zone files and configuration for BIND."""

    def __init__(
        self,
        zones_dir: str = os.environ.get("VINYLDNS_BIND_ZONES_DIR", "/etc/bind/vinyldns_zones"),
        vinyldns_zone_config: str = os.environ.get(
            "VINYLDNS_BIND_ZONE_CONFIG", "/etc/bind/named.conf.vinyldns-zones"
        ),
        zone_config: str = os.environ.get("VINYLDNS_BIND_NAMED_CONF", "/etc/bind/named.conf"),
    ):
        self.zones_dir = zones_dir
        self.vinyldns_zone_config = vinyldns_zone_config
        self.zone_config = zone_config
        self._create_lock = Lock()

        os.makedirs(self.zones_dir, exist_ok=True)
        os.chmod(self.zones_dir, 0o755)

    @staticmethod
    def _validate_zone_name(zone_name: str) -> str:
        zone_name = zone_name.strip()
        if not zone_name or len(zone_name) > 254:
            raise HTTPException(status_code=400, detail="Invalid zoneName")

        normalized = zone_name[:-1] if zone_name.endswith(".") else zone_name
        labels = normalized.split(".")
        label_pattern = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$")

        if any(
            not label or len(label) > 63 or not label_pattern.fullmatch(label)
            for label in labels
        ):
            raise HTTPException(status_code=400, detail="Invalid zoneName")

        return normalized

    def _zone_file_path(self, zone_name: str) -> str:
        validated = self._validate_zone_name(zone_name)
        zones_dir_real = os.path.realpath(self.zones_dir)
        zone_file_path = os.path.realpath(os.path.join(zones_dir_real, validated))

        # Defense-in-depth: confirm the resolved path stays within zones_dir.
        if os.path.commonpath([zone_file_path, zones_dir_real]) != zones_dir_real:
            raise HTTPException(status_code=400, detail="Invalid zoneName")

        return zone_file_path

    def _zone_config_exists(self, zone_name: str) -> bool:
        if not os.path.exists(self.vinyldns_zone_config):
            return False

        with open(self.vinyldns_zone_config, "r", encoding="utf-8") as f:
            content = f.read()

        normalized = self._validate_zone_name(zone_name)
        pattern = rf'^\s*zone\s+"{re.escape(normalized)}\.?"\s*\{{'
        return bool(re.search(pattern, content, re.MULTILINE))

    def zone_exists(self, zone_name: str) -> bool:
        normalized = self._validate_zone_name(zone_name)
        return (
            os.path.exists(self._zone_file_path(normalized))
            or self._zone_config_exists(normalized)
        )

    def _next_serial(self, zone_name: str) -> str:
        today = datetime.now().strftime("%Y%m%d")
        zone_file_path = self._zone_file_path(zone_name)

        if os.path.exists(zone_file_path):
            with open(zone_file_path, "r", encoding="utf-8") as f:
                content = f.read()

            match = re.search(r"^\s*(\d{10})\s*;\s*Serial\b", content, re.MULTILINE)
            if match:
                existing_serial = match.group(1)
                if existing_serial.startswith(today):
                    return f"{today}{int(existing_serial[8:]) + 1:02d}"

        return f"{today}01"

    @staticmethod
    def _normalize_nameservers(nameservers: List[str]) -> List[str]:
        if not nameservers:
            raise HTTPException(
                status_code=400, detail="At least one nameserver is required"
            )

        normalized_nameservers = []
        for nameserver in nameservers:
            ns = nameserver.strip().rstrip(".")
            if (
                not ns
                or any(ch.isspace() for ch in ns)
                or "/" in ns
                or not re.fullmatch(
                    r"[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
                    r"(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*",
                    ns,
                )
            ):
                raise HTTPException(
                    status_code=400, detail=f"Invalid nameserver: {nameserver}"
                )
            normalized_nameservers.append(ns + ".")

        return normalized_nameservers

    @staticmethod
    def _normalize_admin_email(admin_email: str) -> str:
        # DNS SOA RNAME represents an email address with the first @ replaced by a dot.
        value = admin_email.strip()
        if value.count("@") != 1:
            raise HTTPException(status_code=400, detail="Invalid admin_email")

        local, domain = value.split("@", 1)
        if not local or not domain or any(ch.isspace() for ch in value):
            raise HTTPException(status_code=400, detail="Invalid admin_email")

        return f"{local}.{domain}."

    def _build_zone_content(
        self,
        zone_name: str,
        nameservers: List[str],
        admin_email: str,
        serial: str,
        ttl: int,
        refresh: int,
        retry: int,
        expire: int,
        negative_cache_ttl: int,
    ) -> str:
        zone_name = self._validate_zone_name(zone_name)
        ns_list = self._normalize_nameservers(nameservers)
        rname = self._normalize_admin_email(admin_email)
        primary_ns = ns_list[0]

        lines = [
            f"$TTL {ttl}",
            f"{zone_name}. IN SOA {primary_ns} {rname} (",
            f"    {serial} ; Serial",
            f"    {refresh} ; Refresh",
            f"    {retry} ; Retry",
            f"    {expire} ; Expire",
            f"    {negative_cache_ttl} ) ; Negative Cache TTL",
            f"{zone_name}. IN NS {primary_ns}",
        ]
        for ns in ns_list[1:]:
            lines.append(f"{zone_name}. IN NS {ns}")

        return "\n".join(lines) + "\n"

    def create_zone_file(
        self,
        zoneName: str,
        nameservers: List[str],
        admin_email: str,
        ttl: int = 3600,
        refresh: int = 604800,
        retry: int = 86400,
        expire: int = 2419200,
        negative_cache_ttl: int = 604800,
        serial: Optional[str] = None,
    ) -> str:
        zoneName = self._validate_zone_name(zoneName)
        is_new_zone_file = serial is None
        serial = serial or datetime.now().strftime("%Y%m%d01")

        zone_content = self._build_zone_content(
            zoneName, nameservers, admin_email, serial, ttl, refresh,
            retry, expire, negative_cache_ttl
        )
        zone_file_path = self._zone_file_path(zoneName)

        # Exclusive creation prevents accidental overwrite of an existing zone file.
        open_mode = "x" if is_new_zone_file else "w"
        with open(zone_file_path, open_mode, encoding="utf-8") as f:
            f.write(zone_content)

        os.chmod(zone_file_path, 0o644)
        logger.info("Created zone file for %s at %s", zoneName, zone_file_path)
        return zone_file_path

    def add_zone_config(self, zoneName: str, zone_file_path: str) -> None:
        zoneName = self._validate_zone_name(zoneName)
        existing_content = ""

        if os.path.exists(self.vinyldns_zone_config):
            with open(self.vinyldns_zone_config, "r", encoding="utf-8") as f:
                existing_content = f.read()

        zone_pattern = rf'^\s*zone\s+"{re.escape(zoneName)}\.?"\s*\{{'
        zone_match = re.search(zone_pattern, existing_content, re.MULTILINE)

        if not zone_match:
            # New zones must allow both authenticated updates and authenticated AXFR.
            config_content = (
                f'\nzone "{zoneName}" {{\n'
                "    type master;\n"
                f'    file "{zone_file_path}";\n'
                '    allow-update { key "vinyldns."; };\n'
                '    allow-transfer { key "vinyldns."; };\n'
                "};\n"
            )
            existing_content += config_content
            with open(self.vinyldns_zone_config, "w", encoding="utf-8") as f:
                f.write(existing_content)
        else:
            # Existing zone blocks may predate allow-transfer. Find the matching
            # closing brace and add the directive only when it is missing.
            opening_brace = existing_content.find("{", zone_match.start())
            depth = 0
            closing_brace = None
            for index in range(opening_brace, len(existing_content)):
                if existing_content[index] == "{":
                    depth += 1
                elif existing_content[index] == "}":
                    depth -= 1
                    if depth == 0:
                        closing_brace = index
                        break

            if closing_brace is None:
                raise RuntimeError(f"Malformed BIND zone block for {zoneName}")

            zone_block = existing_content[zone_match.start():closing_brace + 1]
            if not re.search(r'^\s*allow-transfer\s*\{', zone_block, re.MULTILINE):
                existing_content = (
                    existing_content[:closing_brace]
                    + '    allow-transfer { key "vinyldns."; };\n'
                    + existing_content[closing_brace:]
                )
                with open(self.vinyldns_zone_config, "w", encoding="utf-8") as f:
                    f.write(existing_content)

        include_line = f'include "{self.vinyldns_zone_config}";'
        with open(self.zone_config, "r", encoding="utf-8") as f:
            main_config = f.read()

        if include_line not in main_config:
            with open(self.zone_config, "a", encoding="utf-8") as f:
                f.write(f"\n{include_line}\n")

        logger.info("Ensured BIND zone configuration for %s", zoneName)

    def update_zone_file(
        self,
        zoneName: str,
        nameservers: List[str],
        admin_email: str,
        ttl: int = 3600,
        refresh: int = 604800,
        retry: int = 86400,
        expire: int = 2419200,
        negative_cache_ttl: int = 604800,
    ) -> str:
        zoneName = self._validate_zone_name(zoneName)
        zone_file_path = self._zone_file_path(zoneName)

        if not os.path.exists(zone_file_path):
            raise HTTPException(status_code=404, detail=f"Zone '{zoneName}' does not exist")

        serial = self._next_serial(zoneName)
        content = self._build_zone_content(
            zoneName, nameservers, admin_email, serial, ttl, refresh,
            retry, expire, negative_cache_ttl
        )

        with open(zone_file_path, "w", encoding="utf-8") as f:
            f.write(content)

        os.chmod(zone_file_path, 0o644)
        logger.info("Updated zone file for %s with serial %s", zoneName, serial)
        return zone_file_path

    def delete_zone(self, zoneName: str) -> Tuple[bool, Optional[str]]:
        zoneName = self._validate_zone_name(zoneName)
        zone_file_path = self._zone_file_path(zoneName)

        if not os.path.exists(zone_file_path) and not self._zone_config_exists(zoneName):
            return False, "Zone does not exist"

        try:
            if os.path.exists(zone_file_path):
                os.remove(zone_file_path)
                logger.info("Deleted zone file: %s", zone_file_path)

            if os.path.exists(self.vinyldns_zone_config):
                with open(self.vinyldns_zone_config, "r", encoding="utf-8") as f:
                    lines = f.readlines()

                new_lines = []
                inside_zone_block = False
                brace_count = 0
                zone_start_pattern = re.compile(
                    rf'^\s*zone\s+"{re.escape(zoneName)}\.?"\s*\{{'
                )

                for line in lines:
                    if not inside_zone_block and zone_start_pattern.search(line):
                        inside_zone_block = True
                        brace_count = line.count("{") - line.count("}")
                        continue

                    if inside_zone_block:
                        brace_count += line.count("{") - line.count("}")
                        if brace_count <= 0:
                            inside_zone_block = False
                        continue

                    new_lines.append(line)

                with open(self.vinyldns_zone_config, "w", encoding="utf-8") as f:
                    f.writelines(new_lines)

            success, error = self.reload_bind(zoneName)
            if not success:
                return False, f"Zone deleted, but BIND reload failed: {error}"

            return True, None
        except Exception as exc:
            logger.exception("Failed to delete zone %s", zoneName)
            return False, str(exc)

    @staticmethod
    def _run_command(command: List[str], timeout: int = 10, check: bool = False):
        """Run a subprocess with captured text output and a timeout."""
        return subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            universal_newlines=True,
            timeout=timeout,
            check=check,
        )

    def reload_bind(self, zoneName: str) -> Tuple[bool, Optional[str]]:
        """
        Reload BIND without restarting it, so parallel test runs never hit a downtime window.

        - rndc reconfig loads new zone blocks (and drops removed ones) from named.conf.
        - rndc freeze/thaw reloads the zone file from disk; a plain `rndc reload` fails for
          dynamic zones (allow-update) with a "dynamic zone" error.
        """
        zoneName = self._validate_zone_name(zoneName)

        try:
            config_result = self._run_command(["named-checkconf", self.zone_config])
            if config_result.returncode != 0:
                return False, (
                    config_result.stderr or config_result.stdout
                    or "BIND config validation failed"
                )

            zone_file_path = self._zone_file_path(zoneName)
            zone_file_exists = os.path.exists(zone_file_path)

            if zone_file_exists:
                zone_result = self._run_command(
                    ["named-checkzone", zoneName, zone_file_path]
                )
                if zone_result.returncode != 0:
                    return False, (
                        zone_result.stderr or zone_result.stdout
                        or "Zone validation failed"
                    )

            try:
                self._run_command(["rndc", "reconfig"], check=True)
            except subprocess.CalledProcessError as exc:
                return False, exc.stderr or exc.stdout or "BIND reconfig failed"

            if zone_file_exists:
                try:
                    self._run_command(["rndc", "freeze", zoneName], check=True)
                    self._run_command(["rndc", "thaw", zoneName], check=True)
                except subprocess.CalledProcessError as exc:
                    return False, exc.stderr or exc.stdout or "BIND zone reload failed"

            logger.info("BIND reloaded successfully via rndc")
            return True, None

        except subprocess.TimeoutExpired:
            logger.exception("BIND validation/reload timed out")
            return False, "Timeout during configuration or zone validation"
        except Exception as exc:
            logger.exception("Unexpected BIND reload error")
            return False, str(exc)


app = FastAPI(
    title="BIND DNS Management API",
    description="API for creating VinylDNS BIND DNS zones and configurations",
    version="1.0.0",
)
dns_manager = VinylDNSBindDNSManager()


class ZoneCreateRequest(BaseModel):
    zoneName: str
    nameservers: List[str]
    admin_email: EmailStr
    ttl: Optional[int] = 3600
    refresh: Optional[int] = 604800
    retry: Optional[int] = 86400
    expire: Optional[int] = 2419200
    negative_cache_ttl: Optional[int] = 604800

    @field_validator("zoneName")
    @classmethod
    def validate_zone_name(cls, value):
        return VinylDNSBindDNSManager._validate_zone_name(value)


class APIResponse(BaseModel):
    success: bool
    message: str
    data: Optional[dict] = None


@app.post("/api/zones/generate", response_model=APIResponse)
async def create_zone(zone_request: ZoneCreateRequest):
    zone_name = zone_request.zoneName
    logger.info("Create zone request: %s", zone_name)

    # Serialize create checks/writes within this API process to reduce race conditions.
    with dns_manager._create_lock:
        if dns_manager.zone_exists(zone_name):
            raise HTTPException(
                status_code=409,
                detail=f"Zone '{zone_name}' already exists; use PUT /api/zones/update to modify it",
            )

        try:
            zone_file = dns_manager.create_zone_file(
                zoneName=zone_name,
                nameservers=zone_request.nameservers,
                admin_email=str(zone_request.admin_email),
                ttl=zone_request.ttl,
                refresh=zone_request.refresh,
                retry=zone_request.retry,
                expire=zone_request.expire,
                negative_cache_ttl=zone_request.negative_cache_ttl,
            )
            dns_manager.add_zone_config(zone_name, zone_file)
            success, error = dns_manager.reload_bind(zone_name)

            if not success:
                # Keep files for diagnosis rather than silently removing partial configuration.
                raise HTTPException(
                    status_code=500,
                    detail=f"Failed to reload VinylDNS BIND: {error}",
                )

            return APIResponse(
                success=True,
                message=f"VinylDNS zone {zone_name} created successfully",
                data={"zoneName": zone_name, "zone_file": zone_file},
            )
        except HTTPException:
            raise
        except FileExistsError:
            raise HTTPException(status_code=409, detail=f"Zone '{zone_name}' already exists")
        except Exception as exc:
            logger.exception("Zone creation failed for %s", zone_name)
            raise HTTPException(status_code=500, detail=str(exc))


@app.put("/api/zones/update", response_model=APIResponse)
async def update_zone(zone_request: ZoneCreateRequest):
    zone_name = zone_request.zoneName
    if not os.path.exists(dns_manager._zone_file_path(zone_name)):
        raise HTTPException(status_code=404, detail=f"Zone '{zone_name}' does not exist")

    try:
        zone_file = dns_manager.update_zone_file(
            zoneName=zone_name,
            nameservers=zone_request.nameservers,
            admin_email=str(zone_request.admin_email),
            ttl=zone_request.ttl,
            refresh=zone_request.refresh,
            retry=zone_request.retry,
            expire=zone_request.expire,
            negative_cache_ttl=zone_request.negative_cache_ttl,
        )
        success, error = dns_manager.reload_bind(zone_name)

        if not success:
            raise HTTPException(
                status_code=500, detail=f"Failed to reload VinylDNS BIND: {error}"
            )

        return APIResponse(
            success=True,
            message=f"VinylDNS zone {zone_name} updated successfully",
            data={"zoneName": zone_name, "zone_file": zone_file},
        )
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Zone update failed for %s", zone_name)
        raise HTTPException(status_code=500, detail=str(exc))


@app.delete("/api/zones/delete", response_model=APIResponse)
async def delete_zone(zoneName: str):
    zone_name = VinylDNSBindDNSManager._validate_zone_name(zoneName)
    logger.info("Delete zone request: %s", zone_name)

    try:
        success, error = dns_manager.delete_zone(zone_name)
        if not success:
            raise HTTPException(status_code=404, detail=error or "Zone not found")

        # delete_zone already reloads BIND; do not reload a second time here.
        return APIResponse(
            success=True,
            message=f"VinylDNS zone {zone_name} deleted and BIND reloaded successfully",
            data={"zoneName": zone_name},
        )
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Zone deletion failed for %s", zone_name)
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/api/health", response_model=APIResponse)
async def health_check():
    return APIResponse(
        success=True, message="VinylDNS BIND management API is running"
    )


if __name__ == "__main__":
    uvicorn.run(
        "manage_vinyldns_zones_bind_api:app",
        host="0.0.0.0",
        port=19000,
        reload=False,
    )
