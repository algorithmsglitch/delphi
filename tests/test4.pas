{ Test 4: Interfaces (BONUS) }
program Test4;

type
  { Interface declaration }
  IShape = interface
    function GetArea: Integer;
    function GetPerimeter: Integer;
    procedure Describe;
  end;

  IPrintable = interface
    procedure Print;
  end;

  { Classes implementing interface }
  TSquare = class(IShape, IPrintable)
  private
    FSide: Integer;
  public
    constructor Create(Side: Integer);
    function GetArea: Integer;
    function GetPerimeter: Integer;
    procedure Describe;
    procedure Print;
  end;

  TCircleApprox = class(IShape)
  private
    FRadius: Integer;
  public
    constructor Create(Radius: Integer);
    function GetArea: Integer;
    function GetPerimeter: Integer;
    procedure Describe;
  end;

constructor TSquare.Create(Side: Integer);
begin
  FSide := Side;
end;

function TSquare.GetArea: Integer;
begin
  Result := FSide * FSide;
end;

function TSquare.GetPerimeter: Integer;
begin
  Result := 4 * FSide;
end;

procedure TSquare.Describe;
begin
  WriteLn('Square with side = ', FSide);
  WriteLn('  Area      = ', GetArea);
  WriteLn('  Perimeter = ', GetPerimeter);
end;

procedure TSquare.Print;
begin
  WriteLn('[Square: side=', FSide, ']');
end;

constructor TCircleApprox.Create(Radius: Integer);
begin
  FRadius := Radius;
end;

function TCircleApprox.GetArea: Integer;
begin
  { Using approx: pi ~ 3 }
  Result := 3 * FRadius * FRadius;
end;

function TCircleApprox.GetPerimeter: Integer;
begin
  { Circumference approx: 2 * 3 * r }
  Result := 6 * FRadius;
end;

procedure TCircleApprox.Describe;
begin
  WriteLn('Circle (approx) with radius = ', FRadius);
  WriteLn('  Area (approx)      = ', GetArea);
  WriteLn('  Perimeter (approx) = ', GetPerimeter);
end;

var
  Sq: TSquare;
  Ci: TCircleApprox;

begin
  WriteLn('=== Test 4: Interfaces (BONUS) ===');
  WriteLn('');

  Sq := TSquare.Create(7);
  Ci := TCircleApprox.Create(5);

  WriteLn('--- Shape 1 ---');
  Sq.Describe;
  Sq.Print;

  WriteLn('');
  WriteLn('--- Shape 2 ---');
  Ci.Describe;

  WriteLn('');
  WriteLn('Test 4 Complete!');
end.
