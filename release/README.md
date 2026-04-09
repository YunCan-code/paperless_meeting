# release 目录说明

`release/` 用于存放离线发布产物，由 `scripts/intranet/prepare-release.sh` 自动生成。

默认输出结构：

```text
release/<version>/
├─ images/
├─ compose/
├─ scripts/
├─ docs/
├─ runtime/
├─ checksums/
└─ VERSION
```

注意：

- 不要把生成出来的大体积镜像包提交进 Git。
- 发布给会议室主机时，使用 `release/<version>/` 目录或对应的压缩包即可。
