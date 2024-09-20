import math

def main():
    count = 3
    primes = set()

    while True:
        isprime = True
        
        for x in range(2, int(math.sqrt(count) + 1)):
            if count % x == 0: 
                isprime = False
                break
        
        if isprime:
            primes.add(count)
        
        count += 1

if __name__ == "__main__":
    main()
