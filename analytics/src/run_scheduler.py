from src.scheduler.scheduler import start_scheduler, stop_scheduler
import signal
import sys


def handle_shutdown(sig, frame):
    print("Shutting down scheduler...")
    stop_scheduler()
    sys.exit(0)


if __name__ == "__main__":
    signal.signal(signal.SIGINT, handle_shutdown)
    signal.signal(signal.SIGTERM, handle_shutdown)
    
    start_scheduler()
    print("Scheduler running. Press Ctrl+C to stop.")
    
    # Keep the process alive
    try:
        while True:
            pass
    except KeyboardInterrupt:
        handle_shutdown(None, None)