import { Link } from 'react-router'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import type { LmsCourseSummary } from '../types/lms'
import { formatCredits } from '../erp/erpData'

type CourseSummaryProps = {
  course: LmsCourseSummary
}

function Fact({ icon, children }: { icon: NavIconName; children: string }) {
  return (
    <li className="flex items-center gap-3 text-[0.95rem] text-ink">
      <NavIcon name={icon} className="size-5 text-ink-muted" />
      {children}
    </li>
  )
}

export function CourseSummary({ course }: CourseSummaryProps) {
  const credits = Number(course.credits)
  return (
    <article className="group relative h-full rounded-panel border border-t-4 border-line border-t-teal-600 bg-surface px-5 py-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)] hover:border-slate-400 hover:border-t-teal-600 has-[a:focus-visible]:outline-2 has-[a:focus-visible]:outline-offset-2 has-[a:focus-visible]:outline-stacc-primary">
      <p className="text-lg font-bold break-words text-ink">{course.courseCode}</p>
      <h3 className="text-xl leading-7 font-semibold break-words text-portal-navy">
        <Link
          to={`/courses/${course.lmsCourseId}`}
          className="underline underline-offset-4 after:absolute after:inset-0 after:rounded-panel focus-visible:outline-none group-hover:text-stacc-primary"
        >
          {course.courseName}
        </Link>
        <span aria-hidden="true"> →</span>
      </h3>
      <ul className="mt-3 space-y-1.5">
        <Fact icon="courses">{`${formatCredits(credits)} ${credits === 1 ? 'credit' : 'credits'}`}</Fact>
        <Fact icon="clubs">{course.sectionCode ? `Section ${course.sectionCode}` : 'Whole semester'}</Fact>
        {course.faculty.length > 0 && <Fact icon="profile">{course.faculty.join(', ')}</Fact>}
      </ul>
    </article>
  )
}
