"use client"

import type React from "react"

import { useState, useRef, useEffect } from "react"
import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Send, Bot, User, MoreVertical } from "lucide-react"

interface Message {
  id: string
  content: string
  sender: "user" | "bot"
  timestamp: Date
}

const initialMessages: Message[] = [
  {
    id: "1",
    content:
      "Hello! I'm your teaching assistant. I can help you with student management, course planning, and teaching strategies. What would you like assistance with today?",
    sender: "bot",
    timestamp: new Date(Date.now() - 300000),
  },
  {
    id: "2",
    content: "Hi! I need help creating a lesson plan for JavaScript fundamentals.",
    sender: "user",
    timestamp: new Date(Date.now() - 240000),
  },
  {
    id: "3",
    content:
      "I'd be happy to help you create a JavaScript fundamentals lesson plan! Here's a structured approach:\n\n1. **Introduction (10 mins)**\n   - What is JavaScript?\n   - Where is it used?\n\n2. **Core Concepts (30 mins)**\n   - Variables and data types\n   - Functions basics\n   - Control structures\n\n3. **Hands-on Practice (15 mins)**\n   - Simple exercises\n   - Interactive coding\n\n4. **Q&A and Wrap-up (5 mins)**\n\nWould you like me to elaborate on any of these sections?",
    sender: "bot",
    timestamp: new Date(Date.now() - 180000),
  },
]

export function ChatInterface() {
  const [messages, setMessages] = useState<Message[]>(initialMessages)
  const [inputValue, setInputValue] = useState("")
  const [isTyping, setIsTyping] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const mockBotResponses = [
    "That's a great question! Based on your teaching style, I recommend focusing on practical examples that students can relate to.",
    "I can help you create personalized study plans for each student based on their progress and learning pace.",
    "Here are some effective strategies for engaging students in online sessions: 1) Use interactive polls, 2) Break content into smaller chunks, 3) Encourage questions throughout.",
    "I've analyzed your student performance data. Would you like me to identify students who might need additional support?",
    "For better student engagement, consider using gamification elements in your courses. I can suggest specific techniques.",
  ]

  const handleSendMessage = async () => {
    if (!inputValue.trim()) return

    const userMessage: Message = {
      id: Date.now().toString(),
      content: inputValue,
      sender: "user",
      timestamp: new Date(),
    }

    setMessages((prev) => [...prev, userMessage])
    setInputValue("")
    setIsTyping(true)

    // Simulate bot response delay
    setTimeout(() => {
      const botResponse: Message = {
        id: (Date.now() + 1).toString(),
        content: mockBotResponses[Math.floor(Math.random() * mockBotResponses.length)],
        sender: "bot",
        timestamp: new Date(),
      }
      setMessages((prev) => [...prev, botResponse])
      setIsTyping(false)
    }, 2000)
  }

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault()
      handleSendMessage()
    }
  }

  return (
    <Card className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 h-full flex flex-col">
      <CardHeader className="p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-teal-100 dark:bg-teal-900/20 rounded-full flex items-center justify-center">
              <Bot className="h-5 w-5 text-teal-600" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-white">Teaching Assistant</h3>
              <p className="text-sm text-gray-500 dark:text-gray-400">Always available to help</p>
            </div>
          </div>
          <Button variant="ghost" size="sm">
            <MoreVertical className="h-4 w-4" />
          </Button>
        </div>
      </CardHeader>

      <CardContent className="flex-1 p-0 overflow-hidden flex flex-col">
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {messages.map((message) => (
            <div
              key={message.id}
              className={`flex items-start gap-3 ${message.sender === "user" ? "flex-row-reverse" : "flex-row"}`}
            >
              <div
                className={`p-2 rounded-full ${
                  message.sender === "user" ? "bg-teal-100 dark:bg-teal-900/20" : "bg-gray-100 dark:bg-gray-700"
                }`}
              >
                {message.sender === "user" ? (
                  <User className="h-4 w-4 text-teal-600" />
                ) : (
                  <Bot className="h-4 w-4 text-gray-600 dark:text-gray-400" />
                )}
              </div>
              <div className={`max-w-md ${message.sender === "user" ? "text-right" : "text-left"}`}>
                <div
                  className={`p-4 rounded-lg ${
                    message.sender === "user"
                      ? "bg-teal-600 text-white"
                      : "bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white"
                  }`}
                >
                  <p className="text-sm whitespace-pre-wrap">{message.content}</p>
                </div>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-2">
                  {message.timestamp.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                </p>
              </div>
            </div>
          ))}

          {isTyping && (
            <div className="flex items-start gap-3">
              <div className="p-2 bg-gray-100 dark:bg-gray-700 rounded-full">
                <Bot className="h-4 w-4 text-gray-600 dark:text-gray-400" />
              </div>
              <div className="bg-gray-100 dark:bg-gray-700 p-4 rounded-lg">
                <div className="flex space-x-1">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                  <div
                    className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                    style={{ animationDelay: "0.1s" }}
                  ></div>
                  <div
                    className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
                    style={{ animationDelay: "0.2s" }}
                  ></div>
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        <div className="p-4 border-t border-gray-200 dark:border-gray-700">
          <div className="flex items-center gap-2">
            <Input
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="Type your message..."
              className="flex-1"
            />
            <Button
              onClick={handleSendMessage}
              disabled={!inputValue.trim() || isTyping}
              className="bg-teal-600 hover:bg-teal-700"
            >
              <Send className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
