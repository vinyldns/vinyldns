import os
import sys
import logging
from datetime import datetime
import subprocess
from typing import Optional, Tuple, List
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, EmailStr
import uvicorn

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

class VinylDNSBindDNSManager:
    def __init__(self,
                 zones_dir: str = "/etc/bind/vinyldns_zones",
                 vinyldns_zone_config: str = "/etc/bind/named.conf.vinyldns-zones",
                 zone_config: str = "/etc/bind/named.conf"):
        self.zones_dir = zones_dir
        self.vinyldns_zone_config = vinyldns_zone_config
        self.zone_config = zone_config

        try:
            os.makedirs(zones_dir, exist_ok=True)
            os.chmod(zones_dir, 0o755)
        except Exception as e:
            logger.error(f"Failed to VinylDNS create zones directory: {e}")
            raise

    def _next_serial(self, zoneName: str) -> str:
        """
        Compute the next SOA serial, bumping the counter if a zone file already
        exists with today's date so successive updates on the same day are valid.
        """
        import re

        today = datetime.now().strftime("%Y%m%d")
        zone_file_path = os.path.join(self.zones_dir, zoneName)
        if os.path.exists(zone_file_path):
            with open(zone_file_path, 'r') as f:
                content = f.read()
            match = re.search(r'(\d{10})\s*;\s*Serial', content)
            if match:
                existing_serial = match.group(1)
                if existing_serial.startswith(today):
                    return f"{today}{int(existing_serial[8:]) + 1:02d}"
        return f"{today}01"

    def create_zone_file(self, zoneName: str, nameservers: List[str],
                        admin_email: str, ttl: int = 3600, refresh: int = 604800,
                        retry: int = 86400, expire: int = 2419200,
                        negative_cache_ttl: int = 604800, serial: Optional[str] = None) -> str:
        """
        Create (or overwrite) a VinylDNS zone file for BIND DNS server with multiple nameservers
        """
        try:
            admin_email = admin_email.replace('@', '.')
            serial = serial or datetime.now().strftime("%Y%m%d01")
            # Fully-qualify nameservers so BIND treats them as absolute names rather than
            # relative to the zone (which would require a nonsensical in-zone glue record).
            nameservers = [ns if ns.endswith('.') else f"{ns}." for ns in nameservers]
            primary_ns = nameservers[0]
            secondary_ns = nameservers[1:]

            zone_content = f""
            # Add NS records for each nameserver
            zone_content = f"""$TTL    {ttl}
{zoneName}       IN      SOA     {primary_ns} {admin_email}. (
                                 {serial} ; Serial
                                 {refresh} ; Refresh
                                 {retry} ; Retry
                                 {expire} ; Expire
                                 {negative_cache_ttl} ) ; Negative Cache TTL
{zoneName}    IN         NS      {primary_ns}
"""
            for ns in secondary_ns:
                zone_content += f"                     IN        NS      {ns}\n"

            zone_file_path = os.path.join(self.zones_dir, f"{zoneName}")

            with open(zone_file_path, 'w') as f:
                f.write(zone_content)

            os.chmod(zone_file_path, 0o644)
            logger.info(f"Created zone file for {zoneName} at {zone_file_path}")
            return zone_file_path

        except Exception as e:
            logger.error(f"Failed to create zone file: {e}")
            raise

    def add_zone_config(self, zoneName: str, zone_file_path: str) -> None:
        """
        Add VinylDNS zone configuration to BIND config file, skipping zones already registered
        (e.g. from a prior create attempt that failed validation after this file was appended).
        """
        try:
            existing_content = ""
            if os.path.exists(self.vinyldns_zone_config):
                with open(self.vinyldns_zone_config, 'r') as f:
                    existing_content = f.read()

            import re
            if re.search(rf'zone\s+"{re.escape(zoneName)}"\s*{{', existing_content):
                logger.info(f"Zone configuration for {zoneName} already present, skipping append")
            else:
                config_content = f'''
zone "{zoneName}" {{
    type master;
    file "{zone_file_path}";
    allow-update {{ any; }};
}};
'''
                with open(self.vinyldns_zone_config, 'a') as f:
                    f.write(config_content)

            named_config = 'include "/etc/bind/named.conf.vinyldns-zones";'
            with open(self.zone_config, 'r+') as f:
                content = f.read()
                if named_config not in content:
                    f.write(f"\n{named_config}\n")

            logger.info(f"Added VinylDNS zone configuration for {zoneName}")
        except Exception as e:
            logger.error(f"Failed to add VinylDNS zone configuration: {e}")
            raise

    def update_zone_file(self, zoneName: str, nameservers: List[str],
                        admin_email: str, ttl: int = 3600, refresh: int = 604800,
                        retry: int = 86400, expire: int = 2419200,
                        negative_cache_ttl: int = 604800) -> str:
        """
        Regenerate an existing zone's file content with a bumped serial. The zone's
        named.conf block is left untouched since it was already registered on create.
        """
        serial = self._next_serial(zoneName)
        zone_file = self.create_zone_file(
            zoneName=zoneName,
            nameservers=nameservers,
            admin_email=admin_email,
            ttl=ttl,
            refresh=refresh,
            retry=retry,
            expire=expire,
            negative_cache_ttl=negative_cache_ttl,
            serial=serial
        )
        logger.info(f"Updated zone file for {zoneName} at {zone_file} with serial {serial}")
        return zone_file

    def delete_zone(self, zoneName: str) -> Tuple[bool, Optional[str]]:
        """
        Delete VinylDNS zone file and its configuration from named.conf.vinyldns-zones.
        """
        import re

        zone_file_path = os.path.join(self.zones_dir, zoneName)

        if not os.path.exists(zone_file_path):
            logger.warning(f"No zone file found for: {zone_file_path}")
            return False, "No zone records found"

        try:
            os.remove(zone_file_path)
            logger.info(f"Deleted zone file: {zone_file_path}")

            if os.path.exists(self.vinyldns_zone_config):
                with open(self.vinyldns_zone_config, 'r') as f:
                    lines = f.readlines()

                new_lines = []
                inside_zone_block = False
                brace_count = 0

                zone_start_pattern = re.compile(rf'zone\s+"{re.escape(zoneName)}"\s*{{')
                for line in lines:
                    if not inside_zone_block and zone_start_pattern.search(line):
                        inside_zone_block = True
                        brace_count = line.count('{') - line.count('}')
                        continue

                    if inside_zone_block:
                        brace_count += line.count('{') - line.count('}')
                        if brace_count <= 0:
                            inside_zone_block = False
                        continue

                    new_lines.append(line)

                with open(self.vinyldns_zone_config, 'w') as f:
                    f.writelines(new_lines)

                logger.info(f"Removed zone configuration for {zoneName}")
            else:
                logger.warning(f"{self.vinyldns_zone_config} does not exist")
            # Step 3: Restart BIND
            success, error = self.reload_bind(zoneName)
            if not success:
                return False, f"Zone deleted, but BIND reload failed: {error}"

            return True, None

        except Exception as e:
            logger.error(f"Failed to delete zone: {e}")
            return False, str(e)


    def reload_bind(self, zoneName: str) -> Tuple[bool, Optional[str]]:
        """
        Reload BIND configuration with optional zone validation if zone file exists.
        
        For zones with allow-update (dynamic zones):
        - rndc freeze → write zone file → rndc thaw (reloads from disk)
        For new zone blocks appended to named.conf:
        - rndc reconfig (loads new zone configurations)
        
        This approach avoids dropping the server and causing test failures in parallel test runs.
        """
        try:
            # Step 1: Validate BIND config
            check_zone_config_result = subprocess.run(
                ['named-checkconf', self.zone_config],
                capture_output=True,
                text=True,
                timeout=10
            )

            if check_zone_config_result.returncode != 0:
                logger.error(f"VinylDNS BIND config validation failed: {check_zone_config_result.stderr}")
                return False, check_zone_config_result.stderr or check_zone_config_result.stdout or "Config validation failed"

            logger.info("VinylDNS BIND configuration validated successfully")

            zone_file_path = os.path.join(self.zones_dir, zoneName)
            zone_file_exists = os.path.exists(zone_file_path)

            # Step 2: Validate zone file if it exists
            if zone_file_exists:
                check_zone_result = subprocess.run(
                    ['named-checkzone', zoneName, zone_file_path],
                    capture_output=True,
                    text=True,
                    timeout=10
                )

                if check_zone_result.returncode != 0:
                    logger.error(f"VinylDNS Zone file validation failed: {check_zone_result.stderr}")
                    return False, check_zone_result.stderr or check_zone_result.stdout or "Zone validation failed"
                else:
                    logger.info(f"VinylDNS Zone file '{zoneName}' validated successfully")
            else:
                logger.warning(f"Zone file for '{zoneName}' not found. Skipping zone validation (possibly deleted).")

            # Step 3: Reload BIND using rndc (no restart needed)
            # This avoids the downtime caused by pkill/restart which was causing intermittent
            # test failures in parallel test runs. With rndc, BIND stays running continuously.
            
            # First, reload the config to handle any new zone blocks in named.conf.
            # rndc reconfig re-parses the configuration file and loads new zone definitions
            # without restarting the server (replaces: pkill -f 'named -c' && /usr/sbin/named -c ...)
            try:
                reconfig_result = subprocess.run(
                    ['rndc', 'reconfig'],
                    capture_output=True,
                    text=True,
                    timeout=10,
                    check=True
                )
                logger.info("BIND reconfig completed via rndc (new zone configs loaded)")
            except subprocess.CalledProcessError as e:
                logger.error(f"rndc reconfig failed: {e.stderr}")
                return False, e.stderr or e.stdout or "BIND reconfig failed"

            # If zone file exists, freeze then thaw to reload it from disk.
            # This is necessary for dynamic zones (with allow-update { any; }) because:
            # - plain 'rndc reload' fails with "dynamic zone" error
            # - rndc freeze prepares the zone for editing and stops updates
            # - rndc thaw resumes updates and reloads the zone file from disk
            # For non-dynamic zones, this sequence is still harmless and ensures consistency.
            if zone_file_exists:
                try:
                    freeze_result = subprocess.run(
                        ['rndc', 'freeze', zoneName],
                        capture_output=True,
                        text=True,
                        timeout=10,
                        check=True
                    )
                    logger.info(f"Froze zone {zoneName}")

                    thaw_result = subprocess.run(
                        ['rndc', 'thaw', zoneName],
                        capture_output=True,
                        text=True,
                        timeout=10,
                        check=True
                    )
                    logger.info(f"Thawed zone {zoneName} - zone reloaded from disk")

                except subprocess.CalledProcessError as e:
                    logger.error(f"rndc freeze/thaw failed for zone {zoneName}: {e.stderr}")
                    return False, e.stderr or e.stdout or "BIND zone reload failed"

            logger.info("VinylDNS BIND reloaded successfully via rndc (no restart)")
            return True, None

        except subprocess.TimeoutExpired:
            logger.error("Configuration or zone validation timed out")
            return False, "Timeout during configuration or zone validation"

        except Exception as e:
            logger.error(f"Unexpected error: {e}")
            return False, str(e)


# FastAPI Application Setup
app = FastAPI(
    title="BIND DNS Management API",
    description="API for creating VinylDNS BIND DNS zones and configurations",
    version="1.0.0"
)

# Initialize DNS Manager
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

class APIResponse(BaseModel):
    success: bool
    message: str
    data: Optional[dict] = None


# API Endpoints for VinylDNS Bind Management
@app.post("/api/zones/generate", response_model=APIResponse)
async def create_zone(zone_request: ZoneCreateRequest):
    logger.info(f"Creating vinylDNS zone with request: {zone_request}")

    try:

        zone_file = dns_manager.create_zone_file(
            zoneName=zone_request.zoneName,
            nameservers=zone_request.nameservers,
            admin_email=str(zone_request.admin_email),
            ttl=zone_request.ttl,
            refresh=zone_request.refresh,
            retry=zone_request.retry,
            expire=zone_request.expire,
            negative_cache_ttl=zone_request.negative_cache_ttl
        )

        dns_manager.add_zone_config(zone_request.zoneName, zone_file)

        success, error = dns_manager.reload_bind(zone_request.zoneName)
        if not success:
            logger.error(f"Zone reload failed with error: {error}")
            raise HTTPException(
                status_code=500,
                detail=f"Failed to reload vinylDNS BIND: {error}" if error else "Failed to reload vinylDNS BIND: Unknown error"
            )

        return APIResponse(
            success=True,
            message=f"vinylDNS Zone {zone_request.zoneName} created successfully",
            data={
                "zoneName": zone_request.zoneName,
                "zone_file": zone_file
            }
        )

    except Exception as e:
        logger.error(f"VinylDNS Zone creation failed: {e}")
        raise HTTPException(
            status_code=500,
            detail=str(e)
        )

@app.put("/api/zones/update", response_model=APIResponse)
async def update_zone(zone_request: ZoneCreateRequest):
    logger.info(f"Updating vinylDNS zone with request: {zone_request}")

    zone_file_path = os.path.join(dns_manager.zones_dir, zone_request.zoneName)
    if not os.path.exists(zone_file_path):
        raise HTTPException(
            status_code=404,
            detail=f"Zone {zone_request.zoneName} does not exist"
        )

    try:
        zone_file = dns_manager.update_zone_file(
            zoneName=zone_request.zoneName,
            nameservers=zone_request.nameservers,
            admin_email=str(zone_request.admin_email),
            ttl=zone_request.ttl,
            refresh=zone_request.refresh,
            retry=zone_request.retry,
            expire=zone_request.expire,
            negative_cache_ttl=zone_request.negative_cache_ttl
        )

        success, error = dns_manager.reload_bind(zone_request.zoneName)
        if not success:
            logger.error(f"Zone reload failed with error: {error}")
            raise HTTPException(
                status_code=500,
                detail=f"Failed to reload vinylDNS BIND: {error}" if error else "Failed to reload vinylDNS BIND: Unknown error"
            )

        return APIResponse(
            success=True,
            message=f"vinylDNS Zone {zone_request.zoneName} updated successfully",
            data={
                "zoneName": zone_request.zoneName,
                "zone_file": zone_file
            }
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"VinylDNS Zone update failed: {e}")
        raise HTTPException(
            status_code=500,
            detail=str(e)
        )

@app.delete("/api/zones/delete", response_model=APIResponse)
async def delete_zone(zoneName: str):
    logger.info(f"Deleting vinylDNS zone: {zoneName}")

    try:
        success, error = dns_manager.delete_zone(zoneName)
        if not success:
            raise HTTPException(
                status_code=500,
                detail=f"Failed to delete vinylDNS BIND zone: {error}" if error else "Unknown error during zone deletion"
            )

        # Reload BIND after deletion
        reload_success, reload_error = dns_manager.reload_bind(zoneName)
        if not reload_success:
            raise HTTPException(
                status_code=500,
                detail=f"Zone deleted, but BIND reload failed: {reload_error}"
            )

        return APIResponse(
            success=True,
            message=f"vinylDNS Zone {zoneName} deleted and BIND reloaded successfully",
            data={"zoneName": zoneName}
        )

    except Exception as e:
        logger.error(f"Zone deletion failed: {e}")
        raise HTTPException(
            status_code=500,
            detail=str(e)
        )

@app.get("/api/health", response_model=APIResponse)
async def health_check():
    return APIResponse(
        success=True,
        message="VinylDNS Zone creation BIND Service is running"
    )

if __name__ == "__main__":
    uvicorn.run(
        "manage_vinyldns_zones_bind_api:app",
        host="0.0.0.0",
        port=19000,
        reload=False
    )