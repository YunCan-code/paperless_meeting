"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts"

const data = [
  { month: "Jan", watchTime: 2400, uniqueViewers: 1800 },
  { month: "Feb", watchTime: 1398, uniqueViewers: 1200 },
  { month: "Mar", watchTime: 9800, uniqueViewers: 7200 },
  { month: "Apr", watchTime: 3908, uniqueViewers: 2800 },
  { month: "May", watchTime: 4800, uniqueViewers: 3600 },
  { month: "Jun", watchTime: 3800, uniqueViewers: 2900 },
  { month: "Jul", watchTime: 4300, uniqueViewers: 3200 },
]

export function CourseWatchTimeChart() {
  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg p-3 shadow-lg">
          <p className="font-semibold text-gray-900 dark:text-white mb-2">{label}</p>
          {payload.map((entry: any, index: number) => (
            <p key={index} className="text-sm flex items-center gap-2" style={{ color: entry.color }}>
              <div className="w-3 h-3 rounded-full" style={{ backgroundColor: entry.color }}></div>
              {entry.dataKey === "watchTime" ? "Watch Time" : "Unique Viewers"}: {entry.value.toLocaleString()}
              {entry.dataKey === "watchTime" ? " hrs" : ""}
            </p>
          ))}
        </div>
      )
    }
    return null
  }

  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg font-semibold text-gray-900 dark:text-white">Course Watch Time (hrs)</CardTitle>
          <div className="flex items-center gap-4 text-sm">
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 bg-blue-500 rounded-full"></div>
              <span className="text-gray-600 dark:text-gray-400">Watch Time</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 bg-emerald-500 rounded-full"></div>
              <span className="text-gray-600 dark:text-gray-400">Unique Viewers</span>
            </div>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="h-80">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" opacity={0.5} />
              <XAxis dataKey="month" stroke="#6b7280" fontSize={12} fontWeight={500} />
              <YAxis stroke="#6b7280" fontSize={12} fontWeight={500} />
              <Tooltip content={<CustomTooltip />} />
              <Bar dataKey="watchTime" fill="#3B82F6" radius={[4, 4, 0, 0]} stroke="#2563EB" strokeWidth={1} />
              <Bar dataKey="uniqueViewers" fill="#10B981" radius={[4, 4, 0, 0]} stroke="#059669" strokeWidth={1} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}
