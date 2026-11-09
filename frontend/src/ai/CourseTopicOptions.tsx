/** A published topic the student can choose, with its module for grouping. */
export type CourseTopicChoice = { id: number; title: string; moduleId: number; moduleTitle: string }

/** The topics as options grouped by module, in course order. */
export function CourseTopicOptions({ topics }: { topics: CourseTopicChoice[] }) {
  const modules = topics.reduce<{ id: number; title: string; topics: CourseTopicChoice[] }[]>((groups, topic) => {
    const last = groups.at(-1)
    if (last && last.id === topic.moduleId) {
      last.topics.push(topic)
    } else {
      groups.push({ id: topic.moduleId, title: topic.moduleTitle, topics: [topic] })
    }
    return groups
  }, [])
  return modules.map((module) => (
    <optgroup key={module.id} label={module.title}>
      {module.topics.map((topic) => (
        <option key={topic.id} value={topic.id}>
          {topic.title}
        </option>
      ))}
    </optgroup>
  ))
}
