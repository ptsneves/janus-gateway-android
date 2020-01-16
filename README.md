# janus-gateway-android

To try this application try to install janus and janus demos. In
ubuntu it is done by

```bash
sudo apt-get install janus janus-demos
sudo service janus start
cd /usr/share/janus/demos
python -m SimpleHTTPServer 8000
```

Now on your browser you should go to the videoroom demo and publish
your browser's video.

On the android code you should edit values/strings.xml and set the websockets
address to the machine that is running the janus server.
