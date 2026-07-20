# 数据源适配指南

豪帽！的 UI 只依赖 `CatalogDataSource`，因此可以在不修改页面代码的情况下更换后端。

## 推荐实现顺序

1. 先获得站点方提供的公开 API 文档或书面授权。
2. 新建 `AuthorizedCatalogDataSource`，把远程 DTO 映射为仓库内的 `Comic` 与 `ReaderPage`。
3. 所有请求设置超时、域名白名单和统一 User-Agent。
4. 登录令牌使用 Android Keystore 或加密存储，不写入日志。
5. 对 401、403、429、451 和删除状态分别处理，不自动重试绕过。
6. 图片缓存设置容量上限，并提供清除入口。

## 明确不做

- 不把完整网站放入 WebView。
- 不解析或复制官方 APK 的私有协议。
- 不模拟官方签名、设备指纹或付费状态。
- 不绕过 Cloudflare、验证码、登录、地区限制或内容限制。
- 不批量下载站点内容。

## 建议的远程层边界

```kotlin
interface AuthorizedCatalogApi {
    suspend fun featured(): List<RemoteComic>
    suspend fun search(query: String, tag: String?): List<RemoteComic>
    suspend fun pages(comicId: String): List<RemotePage>
}
```

将网络层放在单独模块后，再由 Repository 提供缓存与错误转换。不要把 Cookie、令牌或站点 HTML 传入 Compose 层。
