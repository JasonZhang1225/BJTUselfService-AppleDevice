on run argv
	set sid to item 1 of argv
	set pw to item 2 of argv
	tell application "Simulator" to activate
	delay 1
	tell application "System Events"
		tell process "Simulator"
			set position of window 1 to {100, 100}
			set size of window 1 to {402, 902}
		end tell
	end tell
	delay 0.6
	tell application "System Events"
		click at {284, 487}
		delay 0.5
		keystroke sid
		delay 0.4
		click at {284, 546}
		delay 0.5
		keystroke pw
		delay 0.4
		click at {301, 635}
	end tell
end run
