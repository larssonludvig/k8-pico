#!/bin/python3
import matplotlib.pyplot as plt
import numpy as np
import sys
import json

def is_timing(log: str) -> bool:
	if "ms" not in log:
		return False
	if "after" not in log:
		return False
	return True

def parse_number(log: str):
	i_start = log.find("after")
	i_start = log.find(" ", i_start)

	i_end = log.find("ms")
	num_str = log[i_start:i_end].strip()
	return float(num_str)

def parse_activity(log: str):

	events = [
		"HEARTBEAT",
		"ELECTION_END",
		"ELECTION_START",
		"JOIN_REQUEST",
		"JOIN_REPLY",
		"REMOVE_NODE",
		"LEAVE_REPLY",
		"performance",
		"FETCH_NODE",
		"evaluation reply",
		"CREATE_CONTAINER",
		"Cluster heartbeat",
		"remove container"
	]

	for event in events:
		if event in log:
			return event	

	return "UNKNOWN"


def parse_logs(logs: list[str]):
	timings = {}
	for log in logs:
		if is_timing(log):
			num = parse_number(log)
			act = parse_activity(log)
			timings.setdefault(act, [])
			timings[act].append(num)
	return timings

def create_show_graphs(timings: dict):
	plt.close()
	fig, ax = plt.subplots()
	labels = []
	data = []
	for key, value in timings.items():
		if key == "evaluation reply":
			continue
		labels.append(key)
		data.append(value)

	ax.boxplot(data, patch_artist=True, tick_labels=labels)
	plt.ylabel("Time in ms")
	plt.title("Some title")
	plt.grid(True)
	plt.show()	


def main():
	if len(sys.argv) < 2:
		print("Usage: ./parse_logs FILE_NAME")
		exit(1)
	file_name = sys.argv[1]
	fp = open(file_name, "r")
	content = fp.readlines()
	fp.close()
	timings = parse_logs(content)
	create_show_graphs(timings)




if __name__ == "__main__":
	main()