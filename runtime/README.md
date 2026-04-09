# 运行时离线包准备目录

如果你希望 `scripts/intranet/prepare-release.sh` 在生成发布包时顺带打入 Docker 离线安装包，请先把安装文件放到：

```text
runtime/docker/debs/
```

推荐文件：

- `containerd.io_*.deb`
- `docker-ce-cli_*.deb`
- `docker-ce_*.deb`
- `docker-buildx-plugin_*.deb`
- `docker-compose-plugin_*.deb`

说明：

- 该目录是构建机本地准备区，不会自动下载内容。
- `runtime/docker/debs/*.deb` 已加入 `.gitignore`，避免大包误入仓库。
- 打包脚本会把这里的 `.deb` 自动复制到 `release/<version>/runtime/docker/debs/`。
