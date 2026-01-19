# 登录流程假设（基于 Android 逻辑推断）

> 先行假设，后续可根据实际接口调整。

## 目标站点
- MIS 入口：`https://mis.bjtu.edu.cn/auth/sso/?next=/`
- CAS 登录页：`https://cas.bjtu.edu.cn/auth/login/?next=<urlencoded_next>`
- 验证码图片：`https://cas.bjtu.edu.cn/image/<captcha_id>/`
- 登录成功落地页：`https://mis.bjtu.edu.cn/home/`

## 步骤
1) **进入 MIS 入口**
   - GET `https://mis.bjtu.edu.cn/auth/sso/?next=/`
   - 跟随重定向到 CAS 登录页，拿到最终响应 HTML 和最终 URL。
   - 若最终 URL 已经在 `mis.bjtu.edu.cn/home/`，视为已登录。

2) **解析登录页**（CAS）
   - 从 HTML 中提取：
     - `csrfmiddlewaretoken`（隐藏 input）
     - `id_captcha_0`（验证码 ID）
     - `next`（表单中的下一跳，默认为 `/home/`）
   - 构造验证码图片 URL：`https://cas.bjtu.edu.cn/image/<id_captcha_0>/`

3) **验证码获取与识别**
   - 拉取验证码图片 `https://cas.bjtu.edu.cn/image/<id>/`
   - 方案A：Core ML 模型自动识别（若模型存在且识别成功，则直接提交）
   - 方案B：识别失败时，UI 提供文本框让用户手动输入 `captcha_1`

4) **提交登录表单**
   - POST `https://cas.bjtu.edu.cn/auth/login/?next=<next>`
   - 表单字段：
     - `csrfmiddlewaretoken`
     - `captcha_0`（步骤2提取的验证码 ID）
     - `captcha_1`（用户输入的验证码结果）
     - `loginname`（用户名 / 学号）
     - `password`
     - `next`（同步骤2）
   - 请求头建议：
     - `Referer`: CAS 登录页 URL
     - `Origin`: `https://cas.bjtu.edu.cn`
   - Cookie 自动管理：跟随重定向到 MIS 时，Session Cookie 会被保存。

5) **判定成功**
   - 最终响应 URL 落在 `mis.bjtu.edu.cn/home/` 即视为登录成功。
   - CookieStore 中应存在 session 类 Cookie（如 JSESSIONID）。

## 失败场景
- 验证码/密码错误：最终 URL 仍停留在 CAS 登录页，返回失败。
- 网络错误：抛出错误，包装为 `LoginResult(success: false, message: <error>)`。

## 暂未实现
- 验证码识别（需 Core ML 模型转换）
- 学生姓名/学院解析（当前占位为学号，待后续解析 MIS 首页 HTML）
