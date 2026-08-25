
import urllib.request
import json

url = "https://music.youtube.com/youtubei/v1/browse"
payload = {
    "context": {
        "client": {
            "clientName": "WEB_REMIX",
            "clientVersion": "1.20240801.00.00"
        }
    },
    "browseId": "FEmusic_moods_and_genres_chill"
}
headers = {
    "User-Agent": "Mozilla/5.0",
    "Origin": "https://music.youtube.com",
    "Referer": "https://music.youtube.com/",
    "Content-Type": "application/json"
}
req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
resp = urllib.request.urlopen(req)
data = json.loads(resp.read().decode("utf-8"))

def find_video_ids(d):
    if isinstance(d, dict):
        if "videoId" in d:
            yield d["videoId"]
        for k, v in d.items():
            yield from find_video_ids(v)
    elif isinstance(d, list):
        for item in d:
            yield from find_video_ids(item)

video_ids = list(set(find_video_ids(data)))
print("Video IDs found:", video_ids[:20])

