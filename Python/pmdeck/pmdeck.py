import json
import socket
import threading
import base64
from random import randint

import zeroconf
# from pmdeck import pybonjour
import atexit
import sys
import time

from pmdeck.get_uid import get_uid
from pmdeck.get_ip import get_ip

class DeviceManager:

    def __init__(self):
        self.connected_callback = None
        self.disconnected_callback = None
        self.zconf = zeroconf.Zeroconf()
        self.Decks = {}
        self.load_deck_info()
        return

    def connector_listener(self):
        bind_ip = '0.0.0.0'
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.bind((bind_ip, 5000))
        self.server_socket.listen(5)  # max backlog of connections
        local_ip = get_ip()
        port = self.server_socket.getsockname()[1]

        print('Listening on {}:{}'.format(local_ip, port))

        print("Registering Service")
        service_name = "{}:{}._pmdeck._tcp.local.".format(local_ip, str(port))
        service_type = "_pmdeck._tcp.local."
        desc = {}
        info = zeroconf.ServiceInfo(service_type,
                                    service_name,
                                    socket.inet_aton(local_ip), port, 0, 0,
                                    desc, local_ip + ".")

        self.zconf.register_service(info)

        while True:
            try:
                client_socket, address = self.server_socket.accept()
                print('Accepted connection from {}:{}'.format(address[0], address[1]))
                deck = Deck(client_socket, self)
                deck.read()
            except Exception as e:
                print(e)
                return
        return

    def listen_connections(self):
        self.connector_thread:threading.Thread = threading.Thread(
            target=self.connector_listener
        ).start()
        return

    def stop_listening_connections(self):
        self.server_socket.close()
        return

    def unregister_service(self):
        self.zconf.unregister_all_services()
        return

    def sync_new_device(self):
        return

    def stop_syncing(self):
        return

    def set_on_connected_callback(self, callback):
        self.connected_callback = callback
        return

    def on_connected(self, deck):
        # deck.reset()
        if self.connected_callback:
            self.connected_callback(deck)
        return

    def set_on_disconnected_callback(self, callback):
        self.disconnected_callback = callback
        return

    def on_disconnected(self, deck):
        if self.disconnected_callback:
            self.disconnected_callback(deck)
        return

    def save_deck_info(self):
        try:
            with open('decks.json', 'w') as outfile:
                json.dump(self.Decks, outfile)
        except:
            pass
        return

    def load_deck_info(self):
        try:
            with open('decks.json') as json_file:
                self.Decks = json.load(json_file)
        except:
            pass
        return


class Deck:

    def __init__(self, client_socket: socket.socket, deviceManager:DeviceManager):

        self.id = None
        self.key_callback = None
        self.client_socket: socket.socket = client_socket;
        self.disconnected = False
        self.deviceManager = deviceManager

        return

    def __del__(self):
        return

    def read(self):

        # self.client_socket.settimeout(5)

        def listener():
            while not self.disconnected:
                try:
                    data = self.client_socket.recv(1024)
                    stream = data.decode('utf-8')
                    if (len(stream)> 1):
                        print("Received: {}".format(stream))
                    for msg in list(filter(None, stream.split(';'))):
                        spl = msg.split(":")
                        cmd = spl[0]
                        if(cmd == "PING"):
                            self.send("PONG;")

                        elif(cmd == "PONG"):
                            pass

                        elif (cmd == "CLOSE"):
                            self.disconnect()
                            return

                        elif(cmd == "BTNEVENT"):
                            args = spl[1].split(",")
                            self.on_key_status_change(args[0], args[1])

                        elif(cmd == "CONN"):
                            args = spl[1].split(",")
                            self.id = args[0]
                            if self.id in self.deviceManager.Decks:
                                password = self.deviceManager.Decks[self.id]["pass"]
                                self.send("CONN:{},{};".format(get_uid(), password))
                            else:
                                self.disconnect()

                        elif(cmd == "CONNACCEPT"):
                            self.reset()
                            self.deviceManager.on_connected(self)

                        elif(cmd == "SYNCREQ"):
                            args = spl[1].split(",")
                            self.id = args[0]
                            self.send("SYNCTRY:{},{};".format(get_uid(), randint(100000, 999999)))

                        elif(cmd == "SYNCACCEPT"):
                            args = spl[1].split(",")
                            uid = args[0]
                            password = args[1]
                            self.deviceManager.Decks[uid] = {"connected": True, "pass": password}
                            self.deviceManager.save_deck_info()
                            self.send("CONN:{},{};".format(get_uid(), password))

                except Exception as e:
                    print(e)
                    self.disconnect()
                    return

        self.read_thread = threading.Thread(target=listener)
        self.read_thread.start()

        # def pinger():
        #     while not self.disconnected:
        #         try:
        #             self.send("PING;")
        #             time.sleep(3)
        #         except Exception as e:
        #             print(e)
        #             self.disconnect()
        #             return
        #
        # self.ping_thread = threading.Thread(target=pinger)
        # self.ping_thread.start()

        #self.deviceManager.on_connected(self)
        return

    def send(self, s):
        print("Sent: {}".format(s))
        self.client_socket.send(s.encode("utf-8"))
        return

    def disconnect(self):
        if self.disconnected:
            return

        print("Deck Disconnected")
        # TODO
        self.client_socket.close()

        self.disconnected = True
        return

    def reset(self):
        for i in range(0, 15):
            self.set_key_image_path(str(i), "Assets/empty.png")
        return

    def set_key_image_path(self, key, image_path: str):
        if image_path.endswith(".png"):
            encoded = base64.b64encode(open(image_path, "rb").read())
            self.set_key_image_base64(key, encoded)
        else:
            print("please give a png file, this is not acceptable -> {}".format(image_path))
        return

    def set_key_image_base64(self, key, base64_string):
        print(base64_string);
        encoded = ("IMAGE:" + str(key) + ",").encode('utf-8') + base64_string + ";".encode('utf-8')
        try:
            self.client_socket.send(encoded)
        except Exception as e:
            print(e)
            self.disconnect()
        return

    def set_key_callback(self, callback):
        self.key_callback = callback
        return

    def on_key_status_change(self, key, status):
        if self.key_callback:
            self.key_callback(self, key, status)
        return


