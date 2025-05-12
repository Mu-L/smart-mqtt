该插件为smart-mqtt broker提供TLS/SSL安全传输支持，主要功能包括：

1. 基于PEM格式的证书配置TLS/SSL加密通信
2. 可自定义监听端口和主机地址


## 配置参数

- port: 监听端口
- host: 监听主机地址(可选)
- pem: PEM格式的证书内容

## 使用示例

```yaml
host: 0.0.0.0
port: 8883
pem: |
  -----BEGIN CERTIFICATE-----
  MIIEsTCCAxmgAwIBAgIQb1DqeyVD0+UBTKynNf3oJzANBgkqhkiG9w0BAQsFADCB
  ...
  -----END CERTIFICATE-----
  -----BEGIN PRIVATE KEY-----
  MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC1/iKnsFYqfqtV
  ...
  -----END PRIVATE KEY-----
```

## 技术支持
- 作者：三刀（zhengjunweimail@163.com）
- 供应商：smart-mqtt