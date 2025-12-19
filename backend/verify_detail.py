import requests
import json

try:
    # Use ID 1 or fetch list and get first ID
    list_resp = requests.get("http://127.0.0.1:8000/meetings/")
    meetings = list_resp.json()
    if meetings:
        mid = meetings[0]['id']
        print(f"Fetching detail for ID: {mid}")
        r = requests.get(f"http://127.0.0.1:8000/meetings/{mid}")
        data = r.json()
        print(json.dumps(data, indent=2, ensure_ascii=False))
    else:
        print("No meetings found.")
except Exception as e:
    print(e)
