import os
import sys
import builtins
import threading
from importlib.machinery import SourceFileLoader
import json


# Chaquopy 默认把 sys.exit() 映射为 Java System.exit()，会直接杀死整个 App 进程。
# 第三方站点脚本常在异常路径调用 sys.exit()，搜索时会把进程整体带走。
# 这里恢复标准 Python 语义：exit 只抛 SystemExit，由 Java 侧 PyException 捕获。
def _safe_exit(status=0):
    raise SystemExit(status)


def _thread_excepthook(args):
    # 站点脚本线程内的退出/异常只记录，不允许终止进程
    if args.exc_type is SystemExit:
        return
    sys.stderr.write("Python thread error: %s: %s\n" % (args.exc_type.__name__, args.exc_value))


def install_exit_guard():
    sys.exit = _safe_exit
    os._exit = _safe_exit
    builtins.exit = _safe_exit
    builtins.quit = _safe_exit
    if hasattr(threading, "excepthook"):
        threading.excepthook = _thread_excepthook


install_exit_guard()


def spider(cache, api):
    name = os.path.basename(api)
    path = cache + '/' + name
    download(path, api)
    name = name.split('.')[0]
    return SourceFileLoader(name, path).load_module().Spider()


def download(path, api):
    if api.startswith('http'):
        writeFile(path, redirect(api).content)
    else:
        writeFile(path, str.encode(api))


def writeFile(path, content):
    with open(path, 'wb') as f:
        f.write(content)


def redirect(url):
    import requests
    rsp = requests.get(url, allow_redirects=False, verify=False)
    if 'Location' in rsp.headers:
        return redirect(rsp.headers['Location'])
    else:
        return rsp


def str2json(content):
    return json.loads(content)


def getDependence(ru):
    result = ru.getDependence()
    return result


def getName(ru):
    result = ru.getName()
    return result


def init(ru, extend):
    ru.init(extend)


def homeContent(ru, filter):
    result = ru.homeContent(filter)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def homeVideoContent(ru):
    result = ru.homeVideoContent()
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def categoryContent(ru, tid, pg, filter, extend):
    result = ru.categoryContent(tid, pg, filter, str2json(extend))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def detailContent(ru, array):
    result = ru.detailContent(str2json(array))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def searchContent(ru, key, quick, pg="1"):
    result = ru.searchContent(key, quick, pg)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def playerContent(ru, flag, id, vipFlags):
    result = ru.playerContent(flag, id, str2json(vipFlags))
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def liveContent(ru, url):
    result = ru.liveContent(url)
    return result


def localProxy(ru, param):
    result = ru.localProxy(str2json(param))
    return result


def action(ru, action):
    result = ru.action(action)
    formatJo = json.dumps(result, ensure_ascii=False)
    return formatJo


def destroy(ru):
    ru.destroy()


def run():
    pass


if __name__ == '__main__':
    run()
