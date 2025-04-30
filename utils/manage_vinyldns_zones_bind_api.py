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

    def create_zone_file(self, zoneName: str, nameservers: List[str],
                        admin_email: str, ttl: int = 3600, refresh: int = 604800,
                        retry: int = 86400, expire: int = 2419200,
                        negative_cache_ttl: int = 604800) -> str:
        """
        Create a VinylDNS zone file for BIND DNS server with multiple nameservers
        """
        try:
            admin_email = admin_email.replace('@', '.')
            serial = datetime.now().strftime("%Y%m%d01")
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
        Add VinylDNS zone configuration to BIND config file
        """
        try:
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
        """
        try:
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
            if os.path.exists(zone_file_path):
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

            # Step 3: Restart BIND
            subprocess.run(['pkill', '-f', '/usr/sbin/named'], capture_output=True, text=True, check=True)
            subprocess.run(['/usr/sbin/named', '-c', self.zone_config], capture_output=True, text=True, check=True)

            logger.info("VinylDNS BIND service restarted successfully")
            return True, None

        except subprocess.TimeoutExpired:
            logger.error("Configuration or zone validation timed out")
            return False, "Timeout during configuration or zone validation"

        except subprocess.CalledProcessError as e:
            logger.error(f"Error restarting BIND: {e.stderr}")
            return False, e.stderr or e.stdout or "BIND restart failed"

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
        "manage_vinyldns_bind_api:app",
        host="0.0.0.0",
        port=19000,
        reload=False
    )