const lotteryRoundNumberMap = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']

export function formatLotteryRoundOrder(sortOrder) {
  const value = Number(sortOrder) || 0
  if (value <= 0) return '未排轮次'
  if (value < 10) return `第${lotteryRoundNumberMap[value]}轮`
  if (value === 10) return '第十轮'
  if (value < 20) return `第十${lotteryRoundNumberMap[value - 10]}轮`
  const tens = Math.floor(value / 10)
  const units = value % 10
  const tensLabel = lotteryRoundNumberMap[tens] || String(tens)
  const unitsLabel = units ? (lotteryRoundNumberMap[units] || String(units)) : ''
  return `第${tensLabel}十${unitsLabel}轮`
}

export function getLotterySessionStatusLabel(status) {
  return ({
    idle: '空闲',
    collecting: '收集中',
    ready: '准备就绪',
    rolling: '滚动中',
    result: '结果展示中',
    completed: '全部完成'
  }[status] || status || '空闲')
}

export function getLotteryRoundStatusLabel(status) {
  return ({
    draft: '已创建',
    ready: '待开始',
    finished: '已完成'
  }[status] || status || '已创建')
}

export function getLotteryDisplayRound(session = {}) {
  const rounds = Array.isArray(session.rounds) ? session.rounds : []
  return session.current_round || session.next_round || rounds.find(item => item?.status !== 'finished') || null
}

export function getLotteryRoundDisplay(round, session = {}) {
  if (!round) {
    return { label: '待抽取', className: 'state-pending' }
  }

  const currentRoundId = Number(session.current_round?.id)
  const nextRoundId = Number(session.next_round?.id)
  const roundId = Number(round.id)

  if (currentRoundId === roundId && session.session_status === 'rolling') {
    return { label: '当前抽取', className: 'state-current' }
  }
  if (currentRoundId === roundId && ['result', 'completed'].includes(session.session_status)) {
    return { label: '当前结果', className: 'state-result' }
  }
  if (nextRoundId === roundId) {
    return { label: '下一轮', className: 'state-next' }
  }
  if (round.status === 'finished' || (Array.isArray(round.winners) && round.winners.length > 0)) {
    return { label: '已抽取', className: 'state-finished' }
  }
  return { label: '待抽取', className: 'state-pending' }
}

export function getLotteryRoundHint(round, session = {}) {
  const display = getLotteryRoundDisplay(round, session)
  switch (display.label) {
    case '当前抽取':
      return '这一轮正在现场抽取，停止后会立即生成本轮结果。'
    case '当前结果':
      return '这一轮已经完成，当前结果会继续保留，方便主持人确认后进入下一轮。'
    case '下一轮':
      return '这一轮就是顺序上的下一轮，点击开始抽签后会自动进入。'
    case '已抽取':
      return '这一轮已经抽取完成，顺序固定，不再参与后续调整。'
    default:
      return '这一轮尚未抽取，可通过上移 / 下移调整它在顺序中的位置。'
  }
}

export function getLotteryMobileDescription(session = {}) {
  const displayRound = getLotteryDisplayRound(session)
  if (session.session_status === 'rolling') return '抽签进行中，普通参与者当前不能加入或退出抽签池。'
  if (session.self_service_open === false) {
    return session.joined
      ? '抽签已开始，你已参与本场抽签，当前仅可查看状态与结果。'
      : '抽签已开始，当前不能再加入抽签池，可继续查看现场结果。'
  }
  if (Array.isArray(session.winners) && session.winners.length) return `本轮已产生 ${session.winners.length} 位中签人员。`
  if (displayRound) return `${formatLotteryRoundOrder(displayRound.sort_order)}为「${displayRound.title}」，抽签开始前可自由加入或退出抽签池。`
  return '主持人准备轮次后，这里会开放加入入口。'
}
