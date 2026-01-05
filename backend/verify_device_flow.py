import requests
import time

BASE_URL = "http://localhost:8001"

def verify_app_update_flow():
    print("--- Verifying App Updates ---")
    
    # 1. Check latest (should be empty or none)
    try:
        res = requests.get(f"{BASE_URL}/updates/latest")
        print(f"Initial Latest: {res.json()}")
    except:
        print("Initial Latest: None or Error (Expected if empty)")

    # 2. Release a new version (using download_url for simplicity)
    payload = {
        "version_code": 100,
        "version_name": "1.0.0-beta",
        "release_notes": "Initial Release",
        "is_force_update": False
    }
    # Upload 'fake' file if needed, but endpoint allows download_url OR file.
    # We will use query params for data and send a fake file.
    
    # Actually my release_version API expects query params for metadata if using Form/File,
    # OR form-data fields. Let's check the API signature again in my mind.
    # It used Form(...) implicitly for non-Body fields if File is present?
    # Ah, I defined:
    # version_code: int, version_name: str, ... file: UploadFile
    # In FastAPI, these are Form fields.
    
    data = {
        "version_code": 101,
        "version_name": "1.0.1",
        "release_notes": "Test Release",
        "is_force_update": "false"
    }
    files = {'file': ('test.apk', b'fake apk content', 'application/vnd.android.package-archive')}
    
    res = requests.post(f"{BASE_URL}/updates/", data=data, files=files)
    if res.status_code == 200:
        print("Release Success:", res.json())
    else:
        print("Release Failed:", res.text)
        
    # 3. Check latest again
    res = requests.get(f"{BASE_URL}/updates/latest")
    info = res.json()
    print(f"New Latest: {info.get('version_name')} code={info.get('version_code')}")
    
    assert info['version_code'] == 101


def verify_device_heartbeat():
    print("\n--- Verifying Device Heartbeat ---")
    
    device_data = {
        "device_id": "test-device-001",
        "name": "Integration Test Device",
        "model": "Pixel 6",
        "mac_address": "AA:BB:CC:DD:EE:FF",
        "os_version": "12",
        "app_version": "1.0.0",
        "battery_level": 85,
        "is_charging": True,
        "storage_total": 128000000000,
        "storage_available": 64000000000
    }
    
    # 1. Send Heartbeat (Create)
    res = requests.post(f"{BASE_URL}/devices/heartbeat", json=device_data)
    if res.status_code == 200:
        print("Heartbeat 1 (Create): Success")
    else:
        print("Heartbeat 1 Failed:", res.text)
        
    # 2. List Devices
    res = requests.get(f"{BASE_URL}/devices/")
    devices = res.json()
    print(f"Device List Count: {len(devices)}")
    found = False
    for d in devices:
        if d['device_id'] == "test-device-001":
            print(f"Found Device: {d['name']} - {d['status']}")
            found = True
            break
    
    if not found:
        print("Device not found in list!")

    # 3. Update Device (Heartbeat again with change)
    device_data['app_version'] = "1.0.1"
    res = requests.post(f"{BASE_URL}/devices/heartbeat", json=device_data)
    print("Heartbeat 2 (Update):", res.json()['app_version'])
    
    # 4. Block Device
    d_id = devices[0]['id'] # Assuming it's the first one or we found it
    # Need ID.
    # The list response has ID.
    target_id = [d['id'] for d in devices if d['device_id'] == "test-device-001"][0]
    
    requests.put(f"{BASE_URL}/devices/{target_id}/block")
    print("Blocked Device")
    
    res = requests.get(f"{BASE_URL}/devices/")
    for d in res.json():
        if d['id'] == target_id:
            print(f"Device Status after block: {d['status']}")

if __name__ == "__main__":
    try:
        # verify_app_update_flow()
        verify_device_heartbeat()
        print("\nVerification Finished Successfully")
    except Exception as e:
        print(f"\nVerification Failed: {e}")
