"use client"

import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Search, Bot } from "lucide-react"

const conversations = [
  {
    id: 1,
    name: "Teaching Assistant",
    lastMessage: "I can help you with student management...",
    timestamp: "2 min ago",
    unread: 0,
    isBot: true,
    avatar: null,
  },
  {
    id: 2,
    name: "Alice Johnson",
    lastMessage: "Thank you for the feedback on my assignment!",
    timestamp: "1 hour ago",
    unread: 2,
    isBot: false,
    avatar: "https://i.pravatar.cc/60?img=1",
  },
  {
    id: 3,
    name: "Bob Smith",
    lastMessage: "Can we reschedule tomorrow's session?",
    timestamp: "3 hours ago",
    unread: 1,
    isBot: false,
    avatar: "https://i.pravatar.cc/60?img=2",
  },
  {
    id: 4,
    name: "Carol Davis",
    lastMessage: "I completed the React hooks exercise",
    timestamp: "1 day ago",
    unread: 0,
    isBot: false,
    avatar: "https://i.pravatar.cc/60?img=3",
  },
  {
    id: 5,
    name: "David Wilson",
    lastMessage: "Could you explain the CSS flexbox concept again?",
    timestamp: "2 days ago",
    unread: 3,
    isBot: false,
    avatar: "https://i.pravatar.cc/60?img=4",
  },
]

export function ConversationsList() {
  const [searchTerm, setSearchTerm] = useState("")
  const [selectedConversation, setSelectedConversation] = useState(1)

  const filteredConversations = conversations.filter((conversation) =>
    conversation.name.toLowerCase().includes(searchTerm.toLowerCase()),
  )

  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 h-full">
      <CardHeader className="p-4">
        <CardTitle className="text-lg font-semibold text-gray-900 dark:text-white">Messages</CardTitle>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
          <Input
            placeholder="Search conversations..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-10"
          />
        </div>
      </CardHeader>
      <CardContent className="p-0">
        <div className="space-y-1">
          {filteredConversations.map((conversation) => (
            <div
              key={conversation.id}
              onClick={() => setSelectedConversation(conversation.id)}
              className={`flex items-center gap-3 p-4 cursor-pointer transition-colors ${
                selectedConversation === conversation.id
                  ? "bg-teal-50 dark:bg-teal-900/20 border-r-2 border-teal-600"
                  : "hover:bg-gray-50 dark:hover:bg-gray-700/50"
              }`}
            >
              <div className="relative">
                {conversation.isBot ? (
                  <div className="w-10 h-10 bg-teal-100 dark:bg-teal-900/20 rounded-full flex items-center justify-center">
                    <Bot className="h-5 w-5 text-teal-600" />
                  </div>
                ) : (
                  <img
                    src={conversation.avatar || "/placeholder.svg"}
                    alt={conversation.name}
                    className="w-10 h-10 rounded-full object-cover"
                  />
                )}
                {conversation.unread > 0 && (
                  <Badge className="absolute -top-1 -right-1 bg-red-500 text-white text-xs px-1.5 py-0.5 min-w-[20px] h-5 flex items-center justify-center">
                    {conversation.unread}
                  </Badge>
                )}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between mb-1">
                  <h3 className="font-semibold text-gray-900 dark:text-white text-sm truncate">{conversation.name}</h3>
                  <span className="text-xs text-gray-500 dark:text-gray-400">{conversation.timestamp}</span>
                </div>
                <p className="text-sm text-gray-600 dark:text-gray-300 truncate">{conversation.lastMessage}</p>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
