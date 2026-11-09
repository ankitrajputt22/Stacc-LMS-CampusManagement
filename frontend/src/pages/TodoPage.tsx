import { PageHeader } from '../ui'
import { TodoList } from '../todo/TodoList'

/** /todo: the signed-in account's whole To-Do list. */
export function TodoPage() {
  return (
    <>
      <title>To-Do · Stacc</title>
      <PageHeader title="To-Do" description="Work and requests waiting for you, soonest due first." />
      <div className="mt-6">
        <TodoList />
      </div>
    </>
  )
}
