import { useQuery } from '@tanstack/react-query'
import { academicApi } from '../../api/academic'
import type { Role } from '../../api/auth'
import { schoolApi } from '../../api/school'
import { usersApi } from '../../api/users'

/** Query keys shared by the setup pages, so mutations invalidate the right data. */
export const keys = {
  school: ['school'] as const,
  me: ['me'] as const,
  years: ['years'] as const,
  classes: ['classes'] as const,
  sections: (yearId?: number) => ['sections', yearId] as const,
  subjects: ['subjects'] as const,
  classSubjects: (yearId?: number, classId?: number) => ['class-subjects', yearId, classId] as const,
  assignments: (sectionId?: number) => ['assignments', sectionId] as const,
  yearAssignments: (yearId?: number) => ['assignments', 'year', yearId] as const,
  holidays: (yearId?: number) => ['holidays', yearId] as const,
  users: ['users'] as const,
  staff: (role?: Role) => ['staff', role] as const,
}

export const useSchool = () => useQuery({ queryKey: keys.school, queryFn: schoolApi.get })
export const useYears = () => useQuery({ queryKey: keys.years, queryFn: academicApi.years })
export const useClasses = () => useQuery({ queryKey: keys.classes, queryFn: academicApi.classes })
export const useSubjects = () => useQuery({ queryKey: keys.subjects, queryFn: academicApi.subjects })

export const useSections = (yearId?: number) =>
  useQuery({ queryKey: keys.sections(yearId), queryFn: () => academicApi.sections(yearId), enabled: !!yearId })

export const useYearAssignments = (yearId?: number) =>
  useQuery({
    queryKey: keys.yearAssignments(yearId),
    queryFn: () => academicApi.assignments({ academicYearId: yearId }),
    enabled: !!yearId,
  })

export const useTeachers = () =>
  useQuery({ queryKey: keys.staff('TEACHER'), queryFn: () => usersApi.staff('TEACHER') })
