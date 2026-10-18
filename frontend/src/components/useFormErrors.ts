import { App, type FormInstance } from 'antd'
import { errorMessage, fieldErrors } from '../api/client'

/**
 * Shows a failed save on the form: backend errors for fields the form has go
 * under those fields; anything else (other fields, conflicts, server errors)
 * becomes a toast.
 */
export function useFormErrors() {
  const { message } = App.useApp()
  return (form: FormInstance | null, error: unknown) => {
    const fields = fieldErrors(error)
    const known = form ? new Set(Object.keys(form.getFieldsValue(true))) : new Set<string>()
    const onForm = Object.keys(fields).filter((name) => known.has(name))
    const elsewhere = Object.keys(fields).filter((name) => !known.has(name))

    if (form && onForm.length > 0) {
      form.setFields(onForm.map((name) => ({ name, errors: [fields[name]] })))
    }
    if (elsewhere.length > 0) {
      message.error(elsewhere.map((name) => fields[name]).join('. '))
    } else if (onForm.length === 0) {
      message.error(errorMessage(error))
    }
  }
}
