"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"

const locationData = [
  { location: "Chennai", percentage: 34, color: "bg-teal-500" },
  { location: "Koomapatti", percentage: 22, color: "bg-blue-500" },
  { location: "Salem", percentage: 14, color: "bg-green-500" },
  { location: "Tiruvallur", percentage: 12, color: "bg-orange-500" },
  { location: "Trichy", percentage: 10, color: "bg-purple-500" },
  { location: "Chengalpattu", percentage: 8, color: "bg-gray-500" },
]

export function StudentsByLocation() {
  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
      <CardHeader>
        <CardTitle className="text-lg font-semibold text-gray-900 dark:text-white">Students by Locations</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {locationData.map((item, index) => (
            <div
              key={index}
              className="flex items-center justify-between p-3 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
            >
              <div className="flex items-center gap-3">
                <div className={`w-3 h-3 rounded-full ${item.color}`}></div>
                <span className="font-medium text-gray-900 dark:text-white">{item.location}</span>
              </div>
              <span className="font-bold text-gray-900 dark:text-white">{item.percentage}%</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
