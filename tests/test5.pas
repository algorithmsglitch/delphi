{ Test 5: Terminal I/O with integer numbers using class features }
program Test5;

type
  TCounter = class
  private
    FValue: Integer;
    FMin: Integer;
    FMax: Integer;
  public
    constructor Create(InitVal: Integer; Min: Integer; Max: Integer);
    procedure Increment;
    procedure Decrement;
    procedure Reset;
    function GetValue: Integer;
    procedure PrintStatus;
  end;

constructor TCounter.Create(InitVal: Integer; Min: Integer; Max: Integer);
begin
  FMin := Min;
  FMax := Max;
  FValue := InitVal;
end;

procedure TCounter.Increment;
begin
  if FValue < FMax then
    FValue := FValue + 1
  else
    WriteLn('Counter is at maximum (', FMax, ')');
end;

procedure TCounter.Decrement;
begin
  if FValue > FMin then
    FValue := FValue - 1
  else
    WriteLn('Counter is at minimum (', FMin, ')');
end;

procedure TCounter.Reset;
begin
  FValue := FMin;
end;

function TCounter.GetValue: Integer;
begin
  Result := FValue;
end;

procedure TCounter.PrintStatus;
begin
  WriteLn('Counter value: ', FValue, ' (range: ', FMin, ' to ', FMax, ')');
end;

var
  C: TCounter;
  I: Integer;

begin
  WriteLn('=== Test 5: Terminal I/O with OOP ===');
  WriteLn('');

  C := TCounter.Create(0, 0, 10);
  WriteLn('Counter created.');
  C.PrintStatus;

  WriteLn('');
  WriteLn('Incrementing 5 times...');
  for I := 1 to 5 do
  begin
    C.Increment;
    C.PrintStatus;
  end;

  WriteLn('');
  WriteLn('Decrementing 3 times...');
  for I := 1 to 3 do
  begin
    C.Decrement;
    C.PrintStatus;
  end;

  WriteLn('');
  WriteLn('Attempting 8 increments (will cap at max)...');
  for I := 1 to 8 do
    C.Increment;
  C.PrintStatus;

  WriteLn('');
  WriteLn('Resetting counter...');
  C.Reset;
  C.PrintStatus;

  WriteLn('');
  WriteLn('Final value: ', C.GetValue);
  WriteLn('Test 5 Complete!');
end.
