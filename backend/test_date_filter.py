"""Quick script to verify the date filtering in meetings API"""
import requests
from datetime import datetime

# Change this to your actual backend URL
BASE_URL = "http://localhost:8000"  # or your VPS URL

today = datetime.now().strftime("%Y-%m-%d")
print(f"Today's date: {today}")

# Test 1: Get all meetings (no date filter)
print("\n=== Test 1: All meetings (no filter) ===")
resp = requests.get(f"{BASE_URL}/meetings/", params={"limit": 100})
all_meetings = resp.json()
print(f"Total meetings: {len(all_meetings)}")
for m in all_meetings[:5]:  # Show first 5
    print(f"  - {m['title']}, start_time: {m['start_time']}")

# Test 2: Get today's meetings only
print(f"\n=== Test 2: Today's meetings only (start_date={today}, end_date={today}) ===")
resp = requests.get(f"{BASE_URL}/meetings/", params={
    "limit": 100,
    "start_date": today,
    "end_date": today
})
today_meetings = resp.json()
print(f"Today's meetings: {len(today_meetings)}")
for m in today_meetings:
    print(f"  - {m['title']}, start_time: {m['start_time']}")

# Check if filtering actually worked
print(f"\n=== Summary ===")
print(f"All: {len(all_meetings)}, Today only: {len(today_meetings)}")
if len(today_meetings) == len(all_meetings):
    print("WARNING: Date filter may not be working!")
