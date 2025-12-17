"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Star } from "lucide-react"

const coursesData = [
  {
    course: "HTML & CSS Essentials",
    category: "Web Basics",
    students: 2340,
    completion: 76,
    rating: 4.6,
  },
  {
    course: "Modern JavaScript",
    category: "Programming",
    students: 3120,
    completion: 65,
    rating: 4.7,
  },
  {
    course: "React for Beginners",
    category: "Frontend",
    students: 1980,
    completion: 58,
    rating: 4.5,
  },
]

export function TopCoursesTable() {
  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
      <CardHeader>
        <CardTitle className="text-lg font-semibold text-gray-900 dark:text-white">Your Top Courses</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-200 dark:border-gray-700">
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Course</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Category</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Students</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Completion</th>
                <th className="text-left py-3 px-4 font-semibold text-gray-900 dark:text-white">Rating</th>
              </tr>
            </thead>
            <tbody>
              {coursesData.map((course, index) => (
                <tr
                  key={index}
                  className="border-b border-gray-100 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                >
                  <td className="py-4 px-4 font-medium text-gray-900 dark:text-white">{course.course}</td>
                  <td className="py-4 px-4 text-gray-600 dark:text-gray-400">{course.category}</td>
                  <td className="py-4 px-4 text-gray-900 dark:text-white">{course.students.toLocaleString()}</td>
                  <td className="py-4 px-4 text-gray-900 dark:text-white">{course.completion}%</td>
                  <td className="py-4 px-4">
                    <div className="flex items-center gap-1">
                      <Star className="h-4 w-4 fill-yellow-400 text-yellow-400" />
                      <span className="text-gray-900 dark:text-white">{course.rating}</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}
