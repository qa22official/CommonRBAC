import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 无需登录即可访问 */
    public?: boolean
    /** 访问该路由需要的 MENU 权限码 */
    menuCode?: string
  }
}
