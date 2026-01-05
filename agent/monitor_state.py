import socket
import json
import sys
import time

HOST = '127.0.0.1'
PORT = 5000

def recv_line(sock):
    buf = b''
    while True:
        data = sock.recv(4096)
        if not data:
            return None
        buf += data
        if b'\n' in buf:
            line, buf = buf.split(b'\n', 1)
            return line.decode('utf-8').strip()


def main():
    print(f"monitor_state: connect to {HOST}:{PORT}")
    try:
        sock = socket.create_connection((HOST, PORT), timeout=10)
    except Exception as e:
        print('connect failed:', e)
        sys.exit(1)

    print('connected, waiting for INIT or STATE...')
    try:
        prev_snake_len = None
        while True:
            line = recv_line(sock)
            if line is None:
                print('socket closed')
                break
            if not line:
                continue
            try:
                msg = json.loads(line)
            except Exception as e:
                print('failed parse:', e, 'line=', line)
                continue
            typ = msg.get('type')
            payload = msg.get('payload') or {}
            if typ == 'INIT':
                print('INIT payload:', payload)
                continue
            if typ == 'STATE':
                reward = payload.get('reward', 0.0)
                done = payload.get('done', False)
                snake_len = payload.get('snake_len') or payload.get('snakeLen') or payload.get('snake_len')
                head_x = payload.get('head_x') or payload.get('headX')
                head_y = payload.get('head_y') or payload.get('headY')
                food_x = payload.get('food_x') or payload.get('foodX')
                food_y = payload.get('food_y') or payload.get('foodY')

                # fallback: if snake_len missing, try to infer from board
                if snake_len is None and 'board' in payload:
                    b = payload['board']
                    # count 1s
                    try:
                        cnt = sum(1 for row in b for v in row if v == 1)
                        snake_len = cnt
                    except Exception:
                        snake_len = None

                print(f"[STATE] reward={reward}, done={done}, snake_len={snake_len}, head=({head_x},{head_y}), food=({food_x},{food_y})")

                if prev_snake_len is not None and snake_len is not None and snake_len > prev_snake_len:
                    print('>>> SNAKE GREW: prev_len=', prev_snake_len, 'new_len=', snake_len)
                if reward and float(reward) > 0:
                    print('>>> POSITIVE reward detected:', reward)

                prev_snake_len = snake_len
            else:
                print(f"[MSG] type={typ} payload_keys={list(payload.keys())}")
    finally:
        try:
            sock.close()
        except Exception:
            pass


if __name__ == '__main__':
    main()

