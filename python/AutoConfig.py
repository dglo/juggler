#!/usr/bin/env python

import socket

from DAQConfig import DAQConfig
from Zeroconf import Zeroconf, ServiceInfo

class AutoConfig:
    PORT = 53703

    def __init__(self):
        self.startZeroconf()
        self.setupSocket()
        self.serverLoop()

    def handleClient(self, cfg):
        cfg.writeLine('HOWDY')
        msg = cfg.readLine()
        if msg.find('IAM ') != 0:
            print "Unknown command \"" + msg + "\""
        else:
            print "Saw " + msg[4:]

    def serverLoop(self):
        i = 0
        while i < 5:
            (sock, address) = self.socket.accept()
            print "Connect#" + str(i) + " from " + str(address)

            try:
                cfg = DAQConfig(sock)
            except RuntimeError, errMsg:
                print str(errMsg) + " for " + str(address)
                cfg = None

            try:
                if cfg:
                    try:
                        self.handleClient(cfg)
                    except RuntimeError, errMsg:
                        print str(errMsg) + " for " + str(address)
            finally:
                sock.shutdown(1)
            i = i + 1

        print "closing Zeroconf"
        self.zConf.close()

    def setupSocket(self):
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.socket.bind((socket.gethostname(), AutoConfig.PORT))

        maxConnections = 127
        self.socket.listen(maxConnections)

    def startZeroconf(self):
        self.zConf = Zeroconf()

        # Get local IP address
        localName = socket.gethostbyname(socket.gethostname())
        localIP = socket.inet_aton(localName)

        svc1 = ServiceInfo('_daq._tcp.local.', 'config._daq._tcp.local.',
                           address = localIP, port = AutoConfig.PORT,
                           weight = 0, priority = 0,
                           properties = { 'description' : \
                                          'DAQ Configuration server' },
                           )
        self.zConf.registerService(svc1)
        
if __name__ == '__main__':      
    AutoConfig()
