
import requests
from pathlib import Path

# Spring Boot API base URL
BASE_URL = "http://localhost:3000"

# API endpoint
INGEST_URL = f"{BASE_URL}/api/ingest"

def upload_feed(feed_name, file_path, source_system):
    file_path = Path(file_path)

    if not file_path.exists():
        print(f"File not found: {file_path}")
        return

    url = f"{INGEST_URL}/{feed_name}"

    try:
        with file_path.open("rb") as file:
            files = {
                "file": (file_path.name, file)
            }

            data = {
                "sourceSystem": source_system
            }

            response = requests.post(
                url,
                files=files,
                data=data,
                timeout=60
            )

        print(f"\nFeed: {feed_name}")
        print(f"HTTP Status: {response.status_code}")
        print("Response:")

        try:
            print(response.json())
        except ValueError:
            print(response.text)

    except requests.RequestException as error:
        print(f"Upload failed: {error}")


if __name__ == "__main__":
    print("CAP-04 Python to Spring Boot API Client")
    print("API URL:", BASE_URL)
