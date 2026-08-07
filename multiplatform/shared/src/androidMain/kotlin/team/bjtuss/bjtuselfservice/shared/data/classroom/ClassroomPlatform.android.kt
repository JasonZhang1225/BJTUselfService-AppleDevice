package team.bjtuss.bjtuselfservice.shared.data.classroom

/**
 * Android 经 network_security_config.xml 放行 `yaya.csoci.com` 的明文流量
 * （用户 2026-08-04 授权，与 iOS/desktop 一致）；数据源自身仍锁定精确 origin。
 */
actual val classroomLegacyHttpAvailable: Boolean = true
