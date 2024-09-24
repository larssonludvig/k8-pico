import requests
import json
import socket
import time
import os

def main():
    
    api_url = os.environ.get("K8_DEBUG_TARGET_API")
    if api_url is None:
        print("Target API was not specified, nothing to do. Quitting")
        return
    
    headers = {"Content-Type":"application/json"}
    hostname = socket.gethostname()

    data = {
        "hostname" : hostname,
    }

    while True:
        print("Sending data: ", end="")
        try:
            requests.post(api_url, data=json.dumps(data), headers=headers)
            print("Success!")
        except:
            print(f"Failed POST to {api_url}")
            pass
        time.sleep(4)


if __name__ == "__main__":
    main()