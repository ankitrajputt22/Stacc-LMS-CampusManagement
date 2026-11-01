/** The signed-in person's own profile, from GET /api/v1/me/profile. Contact details are only ever their own. */
export type MyPersonProfile = {
  loginId: string
  /** The preferred name, else the full name, else the login ID while the college has not recorded a profile. */
  displayName: string
  /** False until the college records the profile. */
  recorded: boolean
  fullName: string | null
  preferredName: string | null
  email: string | null
  phone: string | null
  hasPhoto: boolean
}
