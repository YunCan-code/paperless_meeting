import requests
import json
import sys

BASE_URL = "http://localhost:8000"

def test_endpoint(name, url):
    print(f"\n--- Testing Endpoint: {name} ---")
    print(f"URL: {url}")
    try:
        response = requests.get(url)
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            print("Response Length:", len(response.content))
            # Optional: print first 200 chars to verify content
            print("Preview:", response.text[:200])
        else:
            print("Response Body (Error Details):")
            try:
                print(json.dumps(response.json(), indent=2, ensure_ascii=False))
            except:
                print(response.text)
                
    except Exception as e:
        print(f"Exception during request: {e}")

if __name__ == "__main__":
    # Test 1: Meetings List (Simulate Android call)
    # Using today's date if possible, user provided 2026-02-16 in previous example
    today = "2026-02-16"
    if len(sys.argv) > 1:
        today = sys.argv[1]
        
    test_endpoint("Get Meetings", f"{BASE_URL}/meetings/?limit=20&start_date={today}&end_date={today}")
    
    # Test 2: Vote History (Simulate Android call for user 48)
    user_id = 48
    test_endpoint("Get Vote History", f"{BASE_URL}/vote/history?user_id={user_id}&skip=0&limit=20")
