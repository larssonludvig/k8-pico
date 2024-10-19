from flask import Flask, request, Response
import threading

app = Flask(__name__)

global_data = {}
global_lock = threading.Lock()

@app.route("/", methods=["GET", "POST"])
def index():
	if request.method == "POST":
		data = request.get_json()
		hostname = data["hostname"]
		ip = request.remote_addr
		with global_lock:
			global_data.setdefault(hostname, [])
			global_data[hostname].append((ip))

		print("Stored data")
		print(f"hostname: {hostname}, ip: {ip}")

	return Response(status=200)

if __name__ == "__main__":
	app.run(host="0.0.0.0", port=5000)
