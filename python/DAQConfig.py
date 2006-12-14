#!/usr/bin/env python

import socket

class DAQConfig:
    MAX_LINE_LEN = 32767

    def __init__(self, sock):
        self.sock = sock
        self.serverMsg = ''

    def readLine(self):
        line = None
        while not line:
            nl = self.serverMsg.find("\n")
            if nl >= 0:
                line = self.serverMsg[0:nl]
                self.serverMsg = self.serverMsg[nl+1:]
            else:
                chunk = self.sock.recv(DAQConfig.MAX_LINE_LEN)
                if chunk == '':
                    raise RuntimeError, "socket connection broken"
                self.serverMsg = self.serverMsg + chunk

        return line

    def writeLine(self, msg):
        if msg.find("\n") < 0:
            msg = msg + "\n"

        start = 0
        while start < len(msg):
            sent = self.sock.send(msg[start:])
            if sent == 0:
                raise RuntimeError, "socket connection broken"
            start = start + sent

if __name__ == '__main__':      
    sys.stderr.write('DAQConfig is not executable')
    sys.exit(1)
