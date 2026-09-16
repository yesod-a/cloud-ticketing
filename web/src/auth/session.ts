export type RefreshResult = string | { accessToken: string; refreshToken?: string } | false
export type Refresh = (refreshToken: string | null) => Promise<RefreshResult>

export function createSession(refreshRequest: Refresh) {
  let access: string | null = null
  let refreshTokenValue: string | null = null
  let refreshing: Promise<boolean> | null = null
  return {
    token: () => access,
    refreshToken: () => refreshTokenValue,
    set: (value: string | null, refresh?: string | null) => { access = value; if (refresh !== undefined) refreshTokenValue = refresh },
    clear: () => { access = null; refreshTokenValue = null },
    async refresh() {
      if (!refreshing) refreshing = refreshRequest(refreshTokenValue).then(value => {
        if (!value) { access = null; refreshTokenValue = null; return false }
        if (typeof value === 'string') access = value
        else { access = value.accessToken; if (value.refreshToken) refreshTokenValue = value.refreshToken }
        return Boolean(access)
      }).finally(() => { refreshing = null })
      return refreshing
    },
  }
}
