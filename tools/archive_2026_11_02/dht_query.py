# -*- coding: utf-8 -*-
"""用真实的 DHT 协议查询：这个 infoHash 到底有没有 peer。
   自己实现 BEP-5 的 get_peers，不依赖任何 tracker。
"""
import socket, struct, random, time, hashlib

TARGET = bytes.fromhex('E4F8ED9F17D7082B418BF17573ECB5E621A794B5')  # MIDV-186
BOOTSTRAP = [
    ('router.bittorrent.com', 6881),
    ('router.utorrent.com', 6881),
    ('dht.transmissionbt.com', 6881),
    ('dht.libtorrent.org', 25401),
    ('router.bitcomet.com', 6881),
]

MY_ID = bytes(random.randint(0, 255) for _ in range(20))

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.settimeout(4)
sock.bind(('0.0.0.0', 0))
print('本地 UDP 端口:', sock.getsockname()[1])


def bstr(s):
    s = s if isinstance(s, bytes) else s.encode()
    return str(len(s)).encode() + b':' + s


def bint(n):
    return b'i%de' % n


def bencode(o):
    if isinstance(o, bytes):
        return bstr(o)
    if isinstance(o, str):
        return bstr(o)
    if isinstance(o, int):
        return bint(o)
    if isinstance(o, list):
        return b'l' + b''.join(bencode(x) for x in o) + b'e'
    if isinstance(o, dict):
        out = b'd'
        for k in sorted(o.keys()):
            kk = k if isinstance(k, bytes) else k.encode()
            out += bstr(kk) + bencode(o[k])
        return out + b'e'
    raise TypeError(type(o))


def bdecode(d, i=0):
    c = d[i:i + 1]
    if c == b'i':
        j = d.index(b'e', i)
        return int(d[i + 1:j]), j + 1
    if c == b'l':
        i += 1
        out = []
        while d[i:i + 1] != b'e':
            v, i = bdecode(d, i)
            out.append(v)
        return out, i + 1
    if c == b'd':
        i += 1
        out = {}
        while d[i:i + 1] != b'e':
            k, i = bdecode(d, i)
            v, i = bdecode(d, i)
            out[k] = v
        return out, i + 1
    if c == b':':
        raise ValueError('bad')
    j = d.index(b':', i)
    n = int(d[i:j])
    return d[j + 1:j + 1 + n], j + 1 + n


nodes = []      # [(ip, port, id)]
peers_found = set()
txn = [0]


def send_query(ip, port, q, args):
    txn[0] += 1
    t = str(txn[0]).encode()
    msg = bencode({b't': t, b'y': b'q', b'q': q.encode(), b'a': args})
    try:
        sock.sendto(msg, (ip, port))
    except Exception:
        return None
    deadline = time.time() + 3
    while time.time() < deadline:
        try:
            data, addr = sock.recvfrom(4096)
        except socket.timeout:
            return None
        try:
            r, _ = bdecode(data)
        except Exception:
            continue
        if r.get(b't') == t:
            return r
    return None


print('=== 1) 引导 DHT（find_node 自己） ===')
for host, port in BOOTSTRAP:
    try:
        ip = socket.gethostbyname(host)
    except Exception as e:
        print('  %-28s 解析失败 %s' % (host, e))
        continue
    r = send_query(ip, port, 'find_node',
                   {b'id': MY_ID, b'target': MY_ID})
    if r and b'r' in r and b'nodes' in r[b'r']:
        blob = r[b'r'][b'nodes']
        cnt = 0
        for x in range(0, len(blob) - 25, 26):
            nid = blob[x:x + 20]
            nip = socket.inet_ntoa(blob[x + 20:x + 24])
            nport = struct.unpack('>H', blob[x + 24:x + 26])[0]
            nodes.append((nip, nport, nid))
            cnt += 1
        print('  %-28s OK 返回 %d 个节点' % (host, cnt))
    else:
        print('  %-28s 无响应' % host)

print('  引导后节点数:', len(nodes))

print('')
print('=== 2) get_peers 迭代查询（找这个种子的做种者） ===')
queried = set()
rounds = 0
while rounds < 12 and nodes:
    rounds += 1
    batch = []
    for n in nodes:
        key = (n[0], n[1])
        if key in queried:
            continue
        queried.add(key)
        batch.append(n)
        if len(batch) >= 16:
            break
    if not batch:
        break
    for ip, port, nid in batch:
        r = send_query(ip, port, 'get_peers',
                       {b'id': MY_ID, b'info_hash': TARGET})
        if not r:
            continue
        rr = r.get(b'r')
        if not rr:
            continue
        if b'values' in rr:
            for v in rr[b'values']:
                if len(v) == 6:
                    pip = socket.inet_ntoa(v[:4])
                    pport = struct.unpack('>H', v[4:6])[0]
                    peers_found.add((pip, pport))
            print('  轮 %d 收到 values，累计 peer=%d' % (rounds, len(peers_found)))
        if b'nodes' in rr:
            blob = rr[b'nodes']
            for x in range(0, len(blob) - 25, 26):
                nid2 = blob[x:x + 20]
                nip = socket.inet_ntoa(blob[x + 20:x + 24])
                nport = struct.unpack('>H', blob[x + 24:x + 26])[0]
                if (nip, nport) not in queried:
                    nodes.append((nip, nport, nid2))
    print('  轮 %d 结束：已查节点 %d，路由表 %d，peer %d'
          % (rounds, len(queried), len(nodes), len(peers_found)))

print('')
print('=== 3) 结果 ===')
print('DHT 网络里查到的 peer 数:', len(peers_found))
for ip, port in sorted(peers_found):
    s = socket.socket()
    s.settimeout(5)
    try:
        s.connect((ip, port))
        print('  ALIVE %s:%d' % (ip, port))
    except Exception as ex:
        print('  dead  %s:%d (%s)' % (ip, port, type(ex).__name__))
    finally:
        try:
            s.close()
        except Exception:
            pass