
import requests
import json

url = "https://coso.top/api/auth/login"
payload = {"name": "test"}
headers = {"Content-Type": "application/json"}

try:
    response = requests.post(url, json=payload, headers=headers)
    with open("login_result.txt", "w", encoding="utf-8") as f:
        f.write(f"Status: {response.status_code}\n")
        f.write(response.text)
except Exception as e:
    with open("login_result.txt", "w", encoding="utf-8") as f:
        f.write(str(e))
