import requests
from pathlib import Path

BASE_URL = "http://localhost:8080"
INGEST_URL = f"{BASE_URL}/api/ingest"

DATA_DIR = Path(__file__).resolve().parent.parent

def upload_feed(feed_name, file_path, source_system):
    file_path = Path(file_path)

    if not file_path.is_absolute():
        file_path = DATA_DIR / file_path

    if not file_path.exists():
        print(f"File not found: {file_path}")
        return

    url = f"{INGEST_URL}/{feed_name}"

    try:
        with open(file_path, "rb") as f:
            files = {"file": (file_path.name, f)}
            data = {"sourceSystem": source_system}

            response = requests.post(
                url, files=files, data=data, timeout=60
            )

        print(f"\nFeed: {feed_name}")
        print(f"HTTP Status: {response.status_code}")
        print("Response:", response.text)

    except requests.RequestException as error:
        print(f"Upload failed: {error}")


if __name__ == "__main__":
    print("CAP-04 Python to Spring Boot API Client")
    print("API URL:", BASE_URL)
    print("Ready. Call upload_feed() to upload an individual feed.")
