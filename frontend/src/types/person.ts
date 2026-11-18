export type MyPersonProfile = {
  loginId: string
  displayName: string
  recorded: boolean
  fullName: string | null
  preferredName: string | null
  email: string | null
  phone: string | null
  hasPhoto: boolean
  mailingAddress: string | null
  emergencyContacts: EmergencyContact[]
}

export type EmergencyContact = {
  contactId: number
  name: string
  relation: string
  phone: string
}
