# -*- coding: utf-8 -*-
# 用抖音主页 API 直连测：sec_uid 从短链拿，看匿名能返回几条
import json, urllib.request, urllib.parse, re

SEC_UID = "MS4wLjABAAAA34BmocU1o6usWvFV4E5N59HpTH-970rhCEXzVb86IeJLU50j_2hz1QgoYA3Xh8nq"

BASE = ("https://www.douyin.com/aweme/v1/web/aweme/post/?"
        "device_platform=webapp&aid=6383&channel=channel_pc_web&pc_client_type=1"
        "&pc_libra_divert=Windows&support_h265=1&support_dash=1"
        "&version_code=190500&version_name=19.5.0&cookie_enabled=true"
        "&screen_width=1920&screen_height=1080&browser_language=zh-CN"
        "&browser_platform=Win32&browser_name=Chrome&browser_version=120.0.0.0"
        "&browser_online=true&engine_name=Blink&engine_version=120.0.0.0"
        "&os_name=Windows&os_version=10&cpu_core_num=8&device_memory=8"
        "&platform=PC&downlink=10&effective_type=4g&round_trip_time=100")

q = (BASE + "&sec_user_id=" + urllib.parse.quote(SEC_UID)
     + "&max_cursor=0&locate_query=false&show_live_replay_strategy=1"
     + "&need_time_list=1&time_list_query=0&whale_cut_token=&cut_version=1"
     + "&count=18&publish_video_strategy_type=2&from_user_page=1")

try:
    req = urllib.request.Request(q, headers={
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
                      '(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Referer': 'https://www.douyin.com/user/' + SEC_UID,
        'Accept': 'application/json, text/plain, */*',
    })
    r = urllib.request.urlopen(req, timeout=25)
    body = r.read().decode('utf-8', 'ignore')
    print('http=', r.status, 'len=', len(body))
    try:
        d = json.loads(body)
        print('status_code=', d.get('status_code'), 'msg=', d.get('status_msg'))
        al = d.get('aweme_list') or []
        print('aweme_list n=', len(al))
    except Exception:
        print('body head:', body[:300])
except Exception as e:
    print('ERR', type(e).__name__, str(e)[:200])