import requests
import sys
import json
import time
import random

def create_containers(baseUrl, container_names, n) -> list[str]:
	url = baseUrl + "/containers"
	containers = []
	for i in range(n):
		image = random.choice(container_names)
		name = f"{image}-{i}".replace(":", "-")
		containers.append(name)

		data = {
			"name": name,
			"image": image
		}
		print(f"CREATE {image} ({name})", end="")
		resp = requests.post(url, json=data)
		print(f": {resp.status_code}")

	return containers

def delete_containers(baseUrl, containers):
	for cont in containers:
		url = baseUrl + f"/containers/{cont}"
		print(f"DELETE {url}", end="")
		resp = requests.delete(url)
		print(f": {resp.status_code}")


def restart_containers(baseUrl, containers, n):
	conts = random.sample(containers, n)
	for cont in conts:
		url = f"{baseUrl}/{cont}/restart"
		print(f"RESTART {cont}", end="")
		resp = requests.put(url)
		print(f": {resp.status_code}")

def get_logs(baseUrl, containers, n):
	conts = random.sample(containers, n)
	
	for cont in conts:
		url = f"{baseUrl}/{cont}/logs"
		print(f"FETCH LOGS {cont}", end="")
		resp = requests.get(url)
		print(f"{resp.status_code}")

def stop(baseUrl, containers, n):
	conts = random.sample(containers, n)
	for cont in conts:
		url = f"{baseUrl}/{cont}/stop"
		print(f"STOP {cont}", end="")
		resp = requests.put(url)
		print(f": {resp.status_code}")



def main():
	if len(sys.argv) < 2:
		print(f"Usage: {sys.argv[0]} IP:PORT")
		exit(1)

	addr = sys.argv[1]
	url = f"http://{addr}/api"

	container_names = [
		"nginx:alpine",
	]

	conts = create_containers(url, container_names, 50)
	print("Finished creating containers")
	# time.sleep(10)
	# restart_containers(url, conts, 20)
	# get_logs(url, conts, 30)
	# stop(url, conts, 10)
	# time.sleep(10)
	# delete_containers(url, conts)

if __name__ == "__main__":
	main()