#!/usr/bin/env python

import socket, sys

from DAQConfig import DAQConfig
from Zeroconf import Zeroconf, ServiceBrowser

class MyListener(object):
    def __init__(self, component):
        self.component = component
        self.localName = socket.gethostbyname(socket.gethostname())

    def startZeroconf(self):
        self.zConf = Zeroconf()
        self.browser = ServiceBrowser(self.zConf, "_daq._tcp.local.", self)

    def addService(self, server, type, name):
        try:
            if name.find('config.') != 0:
                print "Ignoring " + str(type) + ": " + str(name)
            else:
                info = server.getServiceInfo(type, name)
                if not info:
                    print "Didn't get service info for " + str(type) + ": " + \
                        str(name)
                else:
                    self.dumpServiceInfo(info)

                    addr = socket.inet_ntoa(info.address)

                    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    s.connect((str(addr), info.port))
                    try:
                        cfg = DAQConfig(s)
                        self.talkToServer(cfg)
                    finally:
                        s.shutdown(1)
        finally:
            self.zConf.close()

    def dumpServiceInfo(self, info):
        print 'ServiceInfo:'
        print '  type ' + str(info.type)
        print '  name ' + str(info.name)
        print '  addr ' + str(info.address)
        print '  port ' + str(info.port)
        print '  wght ' + str(info.weight)
        print '  prio ' + str(info.priority)
        print '  srvr ' + str(info.server)

    def removeService(self, server, type, name):
        print "Service", repr(name), "removed"

    def talkToServer(self, cfg):
        msg = cfg.readLine()
        cfg.writeLine('IAM ' + self.component)
        
if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.stderr.write("Please specify component name\n")
        sys.exit(1)

    listener = MyListener(sys.argv[1])
    listener.startZeroconf()
