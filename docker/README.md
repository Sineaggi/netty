# Using the docker images

```
cd /path/to/netty/
```

## centos 6 with java 11

```
docker compose -f docker/docker-compose.yaml -f docker/docker-compose.centos-6.111.yaml run test
```

## aarch64 cross compile for transport-native-epoll on X86_64

```
docker compose -f docker/docker-compose.yaml run cross-compile-aarch64-build
```
The default version of aarch64 gcc is `4.9-2016.02`. Update the parameter `gcc_version` in `docker-compose.yaml` to use a version you want.

etc, etc
