import requests
import json

try:
    r = requests.get("http://127.0.0.1:8000/meetings/")
    data = r.json()
    if data:
        print("First Meeting JSON:")
        print(json.dumps(data[0], indent=2, ensure_ascii=False))
    else:
        print("No meetings found.")
except Exception as e:
    print(e)
