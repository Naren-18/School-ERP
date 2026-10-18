import { useQuery } from '@tanstack/react-query'
import { academicApi } from '../../api/academic'
import { useClasses } from '../settings/queries'

/** Classes, and the subjects a class studies in the year (Setup → Class subjects). */
export function useAcademicQueries(yearId: number | undefined) {
  const { data: classes = [] } = useClasses()
  const classSubjects = useQuery({
    queryKey: ['class-subjects-all', yearId, classes.map((c) => c.id).join(',')],
    queryFn: async () =>
      Object.fromEntries(await Promise.all(classes.map(async (c) => [c.id, await academicApi.classSubjects(yearId!, c.id)] as const))),
    enabled: !!yearId && classes.length > 0,
  })
  return {
    classes,
    subjectsOf: (classId: number | undefined) => (classId ? (classSubjects.data?.[classId] ?? []) : []),
  }
}
