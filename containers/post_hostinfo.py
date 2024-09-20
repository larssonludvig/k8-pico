import requests
import json
import socket
import time

def main():
	
	api_url = "http://localhost/TBD"
	headers = {"Content-Type":"application/json"}
	while True:
		hostname = socket.gethostname()
		ip = socket.gethostbyname(hostname)
		data = {
			"hostname" : hostname,
			"ip" : ip,
		}

		try:
			requests.post(api_url, data=json.dumps(data), headers=headers)
		except:
			pass
		time.sleep(4)


if __name__ == "__main__":
	main()