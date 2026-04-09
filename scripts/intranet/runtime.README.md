# 运行时离线包放置说明

`runtime/` 目录用于放置会议室主机首次安装 Docker 运行时所需的离线包。

当前脚本 `install-offline.sh` 默认按 Debian / Ubuntu 系 Linux 处理，要求将以下文件提前放到：

```text
runtime/docker/debs/
```

建议至少包含这些官方 Docker 离线安装包：

- `containerd.io_*.deb`
- `docker-ce-cli_*.deb`
- `docker-ce_*.deb`
- `docker-buildx-plugin_*.deb`
- `docker-compose-plugin_*.deb`

注意：

- 所有安装包必须与目标主机 CPU 架构一致，默认是 `x86_64 / amd64`。
- 如果目标主机不是 Debian / Ubuntu 体系，请不要直接运行 `install-offline.sh`，需要改成对应发行版的离线安装方式。
- 该目录内容不会由仓库自动生成，需要在联网环境提前下载后再打入离线发布包。
