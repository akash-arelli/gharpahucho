// One Sarvam chat completion. Run from the project root: node --env-file=.env scripts/sarvam-hello.mjs
import { SarvamAIClient } from 'sarvamai'

const client = new SarvamAIClient({ apiSubscriptionKey: process.env.SARVAM_API_KEY })

const response = await client.chat.completionsV2({
  model: 'sarvam-105b',
  messages: [{ role: 'user', content: 'Say hello in Telugu and Hindi, in one short line.' }],
  max_tokens: 1000,
})

console.log(response.choices[0].message.content)
